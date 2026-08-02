package com.c203.limit.domain.payment.service;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.payment.client.TossPaymentClient;
import com.c203.limit.domain.payment.client.TossPaymentClientException;
import com.c203.limit.domain.payment.client.TossPaymentResponse;
import com.c203.limit.domain.payment.dto.request.ConfirmPaymentRequest;
import com.c203.limit.domain.payment.dto.request.CreatePaymentRequest;
import com.c203.limit.domain.payment.dto.response.PaymentReconcileOutcome;
import com.c203.limit.domain.payment.dto.response.PaymentReconcileResponse;
import com.c203.limit.domain.payment.dto.response.PaymentResponse;
import com.c203.limit.domain.payment.entity.Payment;
import com.c203.limit.domain.payment.entity.PaymentMethod;
import com.c203.limit.domain.payment.entity.PaymentStatus;
import com.c203.limit.domain.payment.repository.PaymentRepository;
import com.c203.limit.domain.product.service.ListingReservationView;
import com.c203.limit.domain.product.service.ListingService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결제 요청 생성·조회 유스케이스. 매물 예약 전이는 ListingService에 위임하고, 이 서비스는 멱등성
 * 확인, 결제 레코드 생성, 조회 권한 검사만 담당한다.
 */
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final int MAX_CREATE_ATTEMPTS = 3;
    private static final int IDEMPOTENCY_RECOVERY_ATTEMPTS = 5;
    private static final long RETRY_BACKOFF_MILLIS = 50L;
    private static final String CONFIRM_IDEMPOTENCY_PREFIX = "payment-confirm-";
    private static final String CANCEL_REASON = "구매자가 결제 전 예약을 취소함";
    private static final String REJECTED_RESERVATION_RELEASE_REASON = "결제 승인이 거절되어 예약을 즉시 해제함";

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final ListingService listingService;
    private final TossPaymentClient tossPaymentClient;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(
            PaymentRepository paymentRepository,
            MemberRepository memberRepository,
            ListingService listingService,
            TossPaymentClient tossPaymentClient,
            PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.memberRepository = memberRepository;
        this.listingService = listingService;
        this.tossPaymentClient = tossPaymentClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 동일 매물에 대한 동시 요청은 예약(Listing)과 결제(Payment) 유니크 제약이 서로 다른 순서로
     * 잠기며 MySQL 데드락(1213)으로 이어질 수 있다. 한 트랜잭션 안에서 잡아 복구하면 이미 롤백된
     * 트랜잭션을 계속 사용하게 되므로, 실패한 트랜잭션은 버리고 새 트랜잭션으로 재시도한다.
     *
     * <p>멱등키 유니크 제약 위반({@link DataIntegrityViolationException})은 재시도해도 해소되지
     * 않는 영구적 충돌일 수 있다(다른 buyer가 같은 키를 이미 점유). 발생 즉시 재조회로 복구를
     * 시도하고, 내 것이 아니면 남은 재시도를 소진하지 않고 바로 충돌로 응답한다. 그 외 락 경합은
     * 최대 {@link #MAX_CREATE_ATTEMPTS}회만 재시도하고, 그래도 해소되지 않으면 원 예외를 그대로
     * 던지지 않고 {@link ErrorCode#PAYMENT_REQUEST_CONFLICT}로 정리해 응답 계약을 지킨다.
     */
    public PaymentResponse request(Long buyerId, CreatePaymentRequest request) {
        DataAccessException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_CREATE_ATTEMPTS; attempt++) {
            Optional<Payment> existing =
                    paymentRepository.findByBuyerIdAndIdempotencyKey(buyerId, request.getIdempotencyKey());
            if (existing.isPresent()) {
                return toResponseOrConflict(existing.get(), request);
            }
            try {
                return transactionTemplate.execute(status -> createPayment(buyerId, request));
            } catch (DataIntegrityViolationException exception) {
                return recoverOrConflict(buyerId, request);
            } catch (DataAccessException exception) {
                lastFailure = exception;
                pauseBeforeRetry(attempt);
            }
        }

        log.warn(
                "payment request gave up after {} attempts: buyerId={}, listingId={}",
                MAX_CREATE_ATTEMPTS,
                buyerId,
                request.getListingId(),
                lastFailure);
        throw new BusinessException(ErrorCode.PAYMENT_REQUEST_CONFLICT);
    }

    private PaymentResponse recoverOrConflict(Long buyerId, CreatePaymentRequest request) {
        for (int attempt = 1; attempt <= IDEMPOTENCY_RECOVERY_ATTEMPTS; attempt++) {
            Optional<Payment> existing =
                    paymentRepository.findByBuyerIdAndIdempotencyKey(
                            buyerId, request.getIdempotencyKey());
            if (existing.isPresent()) {
                return toResponseOrConflict(existing.get(), request);
            }
            pause(attempt, IDEMPOTENCY_RECOVERY_ATTEMPTS);
        }
        throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }

    private void pauseBeforeRetry(int attempt) {
        pause(attempt, MAX_CREATE_ATTEMPTS);
    }

    private void pause(int attempt, int maxAttempts) {
        if (attempt >= maxAttempts) {
            return;
        }
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS * attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 같은 매물에 이 구매자의 활성 REQUESTED 결제가 이미 있으면(상품 페이지에서 뒤로 갔다가 다시
     * "구매하기"를 누른 경우 등) 새 결제를 만들지 않고 그 결제를 그대로 이어간다 — idempotencyKey는
     * 매 요청 새로 발급되므로 기존 키 조회로는 이 경우를 잡지 못한다. 이어갈 결제가 없을 때만 새로
     * 예약하고 Payment를 만든다.
     */
    private PaymentResponse createPayment(Long buyerId, CreatePaymentRequest request) {
        Optional<Payment> existingRequested = paymentRepository
                .findTopByListingIdAndBuyer_IdAndStatusOrderByRequestedAtDesc(
                        request.getListingId(), buyerId, PaymentStatus.REQUESTED);
        if (existingRequested.isPresent()) {
            warnIfMoreThanOneActiveRequestedPayment(request.getListingId(), buyerId);
            log.info(
                    "payment request continues existing reservation: paymentId={}, listingId={}, buyerId={}",
                    existingRequested.get().getId(),
                    request.getListingId(),
                    buyerId);
            return continueRequestedPayment(existingRequested.get(), buyerId, request.getMethod());
        }

        ListingReservationView listing = listingService.get(request.getListingId());
        if (listing.sellerId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.SELF_PURCHASE_NOT_ALLOWED);
        }
        listing = listingService.reserve(request.getListingId(), buyerId);
        Member buyer = memberRepository
                .findById(buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Payment payment = paymentRepository.save(
                Payment.request(
                        request.getListingId(),
                        buyer,
                        request.getIdempotencyKey(),
                        BigDecimal.valueOf(listing.price()),
                        request.getMethod()));
        payment.assignProviderOrderId();

        log.info(
                "payment requested: paymentId={}, listingId={}, buyerId={}",
                payment.getId(),
                request.getListingId(),
                buyerId);
        return PaymentResponse.from(payment);
    }

    /**
     * findTopBy...는 "매물 하나당 구매자 하나의 REQUESTED는 최대 1건"이라는 가정 위에서 조용히
     * 최신 1건만 가져온다. 이 가정이 과거 버그나 수동 데이터로 깨져 있어도 예외 없이 넘어가므로,
     * 최소한 운영자가 알아챌 수 있게 개수를 세서 1건 초과면 경고만 남긴다(흐름은 막지 않는다).
     */
    private void warnIfMoreThanOneActiveRequestedPayment(Long listingId, Long buyerId) {
        long count = paymentRepository.countByListingIdAndBuyer_IdAndStatus(
                listingId, buyerId, PaymentStatus.REQUESTED);
        if (count > 1) {
            log.warn(
                    "found more than one active REQUESTED payment for the same listing and buyer, "
                            + "continuing with the most recent one only: listingId={}, buyerId={}, count={}",
                    listingId,
                    buyerId,
                    count);
        }
    }

    private PaymentResponse toResponseOrConflict(Payment payment, CreatePaymentRequest request) {
        if (!payment.getListingId().equals(request.getListingId())
                || payment.getMethod() != request.getMethod()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        return PaymentResponse.from(payment);
    }

    /**
     * 결제창을 새로 열어 명시적으로 재시도할 때 호출한다. 검증·갱신 로직은 상품 페이지에서 다시
     * 구매하기를 눌러 기존 예약을 이어받는 경로({@link #createPayment})와 {@link
     * #continueRequestedPayment}를 공유한다 — 두 경로의 규칙이 서로 어긋나지 않게 하기 위함이다.
     */
    @Transactional
    public PaymentResponse retryAttempt(Long buyerId, Long paymentId, PaymentMethod method) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getBuyer().getId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }
        return continueRequestedPayment(payment, buyerId, method);
    }

    /**
     * REQUESTED 결제를 같은 예약 위에서 이어간다(같은 Payment 레코드를 재사용). 예약(Listing.
     * reservedUntil)은 지금부터 다시 계산해 늘려주고, 결제창에서 다른 수단으로 바꿨을 수 있으므로
     * method도 매번 갱신한다.
     *
     * <p>isReservationActive()만으로는 부족하다 — 매물 반영이 실패해 Payment는 APPROVED/FAILED로
     * 이미 끝났는데 Listing만 RESERVED로 남는 갈라진 상태에서는 예약이 여전히 활성으로 보일 수 있다.
     * 그래서 상태를 직접 검증한다. confirm 호출은 나갔는데 서버가 결과를 확정 못 한 애매한 상태
     * (confirmAttemptedAt != null)도 마찬가지다 — Toss가 실제로는 이미 승인했을 수 있어, 여기서 새
     * providerOrderId 발급을 허용하면 이중 청구로 이어질 수 있다. 이 상태는 관리자 reconcile로만
     * 풀어야 한다.
     */
    private PaymentResponse continueRequestedPayment(Payment payment, Long buyerId, PaymentMethod method) {
        if (payment.getStatus() != PaymentStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.PAYMENT_RETRY_NOT_ALLOWED);
        }
        if (payment.getConfirmAttemptedAt() != null) {
            throw new BusinessException(ErrorCode.PAYMENT_RETRY_NOT_ALLOWED);
        }
        if (!listingService.isReservationActive(payment.getListingId(), buyerId)) {
            throw new BusinessException(ErrorCode.PAYMENT_RETRY_NOT_ALLOWED);
        }

        listingService.renewReservationForBuyer(payment.getListingId(), buyerId);
        payment.retry(method);

        log.info(
                "payment attempt retried: paymentId={}, attemptNo={}, providerOrderId={}, method={}",
                payment.getId(),
                payment.getAttemptNo(),
                payment.getProviderOrderId(),
                payment.getMethod());
        return PaymentResponse.from(payment);
    }

    /**
     * 결제창 진입 전(REQUESTED) 단계에서 구매자가 명시적으로 취소한다. Toss 결제창에서 취소하거나
     * 브라우저를 닫아도 10분 예약 유예가 끝나야 스케줄러가 매물을 풀어주는데, 이 API는 그 대기 없이
     * 즉시 예약을 해제한다 — 스케줄러는 이 호출이 유실됐을 때의 최종 안전망으로 계속 남는다.
     *
     * <p>이미 CANCELLED·EXPIRED인 결제는 사용자가 취소 버튼을 여러 번 누르거나 재접속해도 오류 없이
     * 같은 결과를 그대로 반환한다(멱등). APPROVED·FAILED는 이 API로 되돌릴 수 없는 진행된 결제라
     * 거부한다 — 승인된 결제는 환불 흐름으로 유도해야 한다.
     */
    @Transactional
    public PaymentResponse cancel(Long buyerId, Long paymentId) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getBuyer().getId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }
        if (payment.getStatus() == PaymentStatus.CANCELLED || payment.getStatus() == PaymentStatus.EXPIRED) {
            return PaymentResponse.from(payment);
        }
        if (payment.getStatus() != PaymentStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_CANCELLABLE);
        }

        payment.cancel(CANCEL_REASON);
        listingService.cancelReservation(payment.getListingId(), buyerId, CANCEL_REASON);

        log.info(
                "payment cancelled: paymentId={}, listingId={}, buyerId={}",
                payment.getId(),
                payment.getListingId(),
                buyerId);
        return PaymentResponse.from(payment);
    }

    /**
     * Toss 결제창에서 승인된 결제를 서버 승인 API로 확정한다. orderId·금액은 클라이언트 응답을
     * 신뢰하지 않고 우리가 저장해둔 값과 대조하고, 같은 시도에 대해서는 항상 같은 멱등키
     * (payment-confirm-{paymentId}-{attemptNo})로 Toss confirm을 호출해 중복 승인을 막는다.
     *
     * <p>이미 APPROVED된 결제에 confirm이 다시 들어오면(성공 페이지 새로고침, 응답 유실 후 재시도 등)
     * Toss를 다시 호출하지 않고 기존 승인 결과를 그대로 돌려준다 — 그래야 재시도가 새 결제로 이어지며
     * 카드가 두 번 청구되는 것을 막을 수 있다. paymentKey까지 승인 기록과 일치해야 같은 승인 건으로
     * 인정하고, 다르면 위조 가능성으로 보고 거부한다.
     *
     * <p>Toss 호출은 의도적으로 DB 트랜잭션 밖에서 실행한다 — 외부 호출을 트랜잭션 안에 두면 Toss는
     * 승인했는데 뒤이은 로컬 커밋만 실패하는 불일치가 생길 수 있다. 검증 조회, 실패 기록, 승인 반영은
     * 각각 독립된 트랜잭션으로 나눠 커밋하고, 매물 반영 단계는 별도로 실패를 허용해 Toss가 이미
     * 승인한 결제 자체는 절대 유실하지 않는다.
     */
    public PaymentResponse confirm(Long buyerId, Long paymentId, ConfirmPaymentRequest request) {
        Payment payment = requireOwnedPayment(buyerId, paymentId);
        requireMatchingOrderAndAmount(payment, request.getOrderId(), request.getAmount());

        if (payment.getStatus() == PaymentStatus.APPROVED) {
            return confirmAlreadyApproved(payment, request.getPaymentKey());
        }
        if (payment.getStatus() != PaymentStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_CONFIRMABLE);
        }
        if (!listingService.isReservationActive(payment.getListingId(), buyerId)) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_RESERVATION_EXPIRED);
        }

        markConfirmAttempted(paymentId);

        String idempotencyKey = CONFIRM_IDEMPOTENCY_PREFIX + payment.getId() + "-" + payment.getAttemptNo();
        TossPaymentResponse tossResponse;
        try {
            tossResponse = tossPaymentClient.confirm(
                    request.getPaymentKey(), request.getOrderId(), request.getAmount(), idempotencyKey);
        } catch (TossPaymentClientException exception) {
            if (exception.isRetryable()) {
                log.warn(
                        "toss confirm retryable failure: paymentId={}, tossCode={}",
                        paymentId,
                        exception.getTossCode(),
                        exception);
                throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_RETRYABLE);
            }
            recordFailure(paymentId, exception.getTossMessage());
            releaseReservationAfterRejection(payment.getListingId(), buyerId, paymentId);
            log.info(
                    "payment confirm rejected: paymentId={}, tossCode={}", paymentId, exception.getTossCode());
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_REJECTED, exception.getTossMessage());
        }

        Long listingId;
        try {
            listingId = approvePayment(paymentId, tossResponse);
        } catch (RuntimeException exception) {
            // Toss는 이미 승인했는데 로컬 DB 반영이 실패한 경우다. 여기서 흔적을 안 남기면 운영자가
            // Toss 콘솔과 대조할 단서가 로그에도 없어진다 — 자동 복구는 하지 않고, 추적 가능하게만 한다.
            log.error(
                    "toss confirm succeeded but local approval could not be persisted: paymentId={}, "
                            + "listingId={}, buyerId={}, providerOrderId={}, amount={}, tossStatus={}",
                    paymentId,
                    payment.getListingId(),
                    buyerId,
                    payment.getProviderOrderId(),
                    tossResponse.totalAmount(),
                    tossResponse.status(),
                    exception);
            throw exception;
        }
        markListingPaidOrEscalate(listingId, buyerId, paymentId, payment.getProviderOrderId());

        log.info(
                "payment confirmed: paymentId={}, listingId={}, buyerId={}", paymentId, listingId, buyerId);
        return PaymentResponse.from(
                paymentRepository
                        .findById(paymentId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND)));
    }

    private Payment requireOwnedPayment(Long buyerId, Long paymentId) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getBuyer().getId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }
        return payment;
    }

    private void requireMatchingOrderAndAmount(Payment payment, String orderId, Long amount) {
        if (!orderId.equals(payment.getProviderOrderId())) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_ID_MISMATCH);
        }
        if (payment.getRequestedAmount().compareTo(BigDecimal.valueOf(amount)) != 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    /**
     * 이미 APPROVED된 결제에 confirm이 재요청된 경우다. orderId·금액은 앞서 확인했으므로 paymentKey가
     * 승인 기록({@link Payment#getProviderTransactionId()})과 일치하는지만 마저 확인한다. 일치하면
     * 같은 승인 건의 재요청으로 보고 Toss를 다시 부르지 않고 기존 결과를 그대로 반환한다(멱등).
     */
    private PaymentResponse confirmAlreadyApproved(Payment payment, String paymentKey) {
        if (!paymentKey.equals(payment.getProviderTransactionId())) {
            log.warn(
                    "payment confirm replay rejected, paymentKey mismatch against approved record: paymentId={}",
                    payment.getId());
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CONFIRMED_MISMATCH);
        }
        log.info("payment confirm replay matched existing approval: paymentId={}", payment.getId());
        return PaymentResponse.from(payment);
    }

    private void markConfirmAttempted(Long paymentId) {
        transactionTemplate.executeWithoutResult(status -> paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND))
                .markConfirmAttempted());
    }

    /**
     * 관리자가 REQUESTED로 남은 결제를 Toss 실제 승인 여부로 대사(reconcile)한다. confirm 호출 후
     * 응답을 못 받아 로컬에는 승인 흔적이 없지만 Toss는 이미 승인했을 수 있는 상황을 위한 수동 복구
     * 경로다. 자동으로는 호출되지 않고 관리자 API와 예약 만료 배치에서만 사용한다.
     */
    public PaymentReconcileResponse reconcile(Long paymentId) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (payment.getStatus() != PaymentStatus.REQUESTED) {
            return PaymentReconcileResponse.of(PaymentReconcileOutcome.NO_ACTION, payment);
        }

        TossPaymentResponse tossResponse;
        try {
            tossResponse = tossPaymentClient.findByOrderId(payment.getProviderOrderId());
        } catch (TossPaymentClientException exception) {
            if (exception.isRetryable()) {
                log.warn(
                        "payment reconcile lookup retryable failure: paymentId={}, tossCode={}",
                        paymentId,
                        exception.getTossCode(),
                        exception);
                throw new BusinessException(ErrorCode.PAYMENT_RECONCILE_RETRYABLE);
            }
            log.info(
                    "payment reconcile lookup found no matching toss record: paymentId={}, tossCode={}",
                    paymentId,
                    exception.getTossCode());
            return PaymentReconcileResponse.of(PaymentReconcileOutcome.NO_ACTION, payment);
        }

        if (!"DONE".equals(tossResponse.status())) {
            return PaymentReconcileResponse.of(PaymentReconcileOutcome.NO_ACTION, payment);
        }
        if (!payment.getProviderOrderId().equals(tossResponse.orderId())
                || payment.getRequestedAmount().compareTo(BigDecimal.valueOf(tossResponse.totalAmount())) != 0) {
            log.error(
                    "payment reconcile mismatch, needs manual review: paymentId={}, providerOrderId={}, "
                            + "tossOrderId={}, requestedAmount={}, tossAmount={}",
                    paymentId,
                    payment.getProviderOrderId(),
                    tossResponse.orderId(),
                    payment.getRequestedAmount(),
                    tossResponse.totalAmount());
            throw new BusinessException(ErrorCode.PAYMENT_RECONCILE_MISMATCH);
        }

        Long listingId = approvePayment(paymentId, tossResponse);
        markListingPaidRecoveredOrEscalate(
                listingId, payment.getBuyer().getId(), paymentId, payment.getProviderOrderId());
        Payment recovered = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        log.info("payment recovered via reconcile: paymentId={}, listingId={}", paymentId, listingId);
        return PaymentReconcileResponse.of(PaymentReconcileOutcome.RECOVERED, recovered);
    }

    private void recordFailure(Long paymentId, String reason) {
        transactionTemplate.executeWithoutResult(status -> paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND))
                .fail(reason));
    }

    /**
     * Toss가 confirm을 명확히 거절한(카드 거절 등) 결제는 재시도해도 성공 가능성이 낮아, 매물을
     * 예약 유예 시간(TTL)이 끝날 때까지 묶어둘 이유가 없다 — 곧바로 예약을 풀어 다른 결제수단으로
     * 새 결제를 시작하거나 다른 구매자가 살 수 있게 한다. 이미 예약 상태가 바뀐 경우(TTL 만료로
     * 스케줄러가 먼저 처리한 경우 등)는 실패로 보지 않고 로그만 남긴다 — 방금 기록한
     * {@link Payment#fail(String)}은 그대로 유지돼야 하므로 별도 트랜잭션으로 분리한다.
     */
    private void releaseReservationAfterRejection(Long listingId, Long buyerId, Long paymentId) {
        try {
            listingService.cancelReservation(listingId, buyerId, REJECTED_RESERVATION_RELEASE_REASON);
        } catch (RuntimeException exception) {
            log.warn(
                    "could not release reservation after payment rejection, listing state may have "
                            + "already changed: paymentId={}, listingId={}, buyerId={}",
                    paymentId,
                    listingId,
                    buyerId,
                    exception);
        }
    }

    private Long approvePayment(Long paymentId, TossPaymentResponse tossResponse) {
        return transactionTemplate.execute(status -> {
            Payment payment = paymentRepository
                    .findById(paymentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
            payment.approve(BigDecimal.valueOf(tossResponse.totalAmount()), tossResponse.paymentKey());
            return payment.getListingId();
        });
    }

    /**
     * 매물 반영이 실패하면(예약 만료·재배정, DB 예외, 낙관적 락 충돌 등 무엇이든) Toss는 이미
     * 승인했으므로 결제 승인 기록은 그대로 두고 별도 에러로 승격한다 — 여기서 롤백해버리면 실제로
     * 돈은 빠져나갔는데 우리 DB에는 승인 흔적이 남지 않는, 더 추적하기 어려운 상태가 된다. 원인이
     * 업무 규칙 위반이든 일시적 인프라 장애든 동일하게 운영자가 이 로그로 정산/환불을 수동 처리해야
     * 하므로 BusinessException으로 좁히지 않고 RuntimeException 전체를 잡는다.
     *
     * <p>일반 confirm() 경로에서는 예약 유예 시간(TTL) 검증이 살아있는 {@link ListingService#markPaid}
     * 를 그대로 써야 한다 — 이미 다른 구매자에게 넘어갔거나 만료된 예약을 결제완료로 덮어쓰면 안
     * 되기 때문이다.
     */
    private void markListingPaidOrEscalate(
            Long listingId, Long buyerId, Long paymentId, String providerOrderId) {
        applyListingPaidTransitionOrEscalate(
                paymentId,
                listingId,
                buyerId,
                providerOrderId,
                () -> listingService.markPaid(listingId, buyerId));
    }

    /**
     * PG 대사(reconcile)로 Toss 승인을 확인한 결제를 복구할 때만 쓴다. 예약 유예 시간이 이미
     * 지났어도 결제완료로 전환하는 {@link ListingService#markPaidRecoveredFromPg}를 호출한다 —
     * 여기서 일반 markPaid()를 쓰면 대사 대상(=TTL이 이미 지난 REQUESTED 건)일수록 항상 실패하는
     * 자기모순이 생긴다.
     */
    private void markListingPaidRecoveredOrEscalate(
            Long listingId, Long buyerId, Long paymentId, String providerOrderId) {
        applyListingPaidTransitionOrEscalate(
                paymentId,
                listingId,
                buyerId,
                providerOrderId,
                () -> listingService.markPaidRecoveredFromPg(listingId, buyerId));
    }

    private void applyListingPaidTransitionOrEscalate(
            Long paymentId, Long listingId, Long buyerId, String providerOrderId, Runnable transition) {
        try {
            transition.run();
        } catch (RuntimeException exception) {
            log.error(
                    "payment approved by PG but listing could not be marked paid, needs manual "
                            + "reconciliation: paymentId={}, listingId={}, buyerId={}, providerOrderId={}",
                    paymentId,
                    listingId,
                    buyerId,
                    providerOrderId,
                    exception);
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_RESERVATION_INVALID);
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long buyerId, Long paymentId) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getBuyer().getId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }
        return PaymentResponse.from(payment);
    }
}
