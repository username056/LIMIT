package com.c203.limit.domain.payment.service;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.payment.client.TossPaymentClient;
import com.c203.limit.domain.payment.client.TossPaymentClientException;
import com.c203.limit.domain.payment.client.TossPaymentResponse;
import com.c203.limit.domain.payment.dto.request.ConfirmPaymentRequest;
import com.c203.limit.domain.payment.dto.request.CreatePaymentRequest;
import com.c203.limit.domain.payment.dto.response.PaymentResponse;
import com.c203.limit.domain.payment.entity.Payment;
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

    private PaymentResponse createPayment(Long buyerId, CreatePaymentRequest request) {
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

    private PaymentResponse toResponseOrConflict(Payment payment, CreatePaymentRequest request) {
        if (!payment.getListingId().equals(request.getListingId())
                || payment.getMethod() != request.getMethod()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        return PaymentResponse.from(payment);
    }

    /**
     * 결제창을 새로 열어 재시도할 때 호출한다. 예약(Listing.reservedUntil)이 이미 만료됐으면
     * attemptNo를 올리지 않고 거부해, 만료된 매물에 새 providerOrderId가 계속 발급되는 것을 막는다.
     */
    @Transactional
    public PaymentResponse retryAttempt(Long buyerId, Long paymentId) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getBuyer().getId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }
        if (!listingService.isReservationActive(payment.getListingId(), buyerId)) {
            throw new BusinessException(ErrorCode.PAYMENT_RETRY_NOT_ALLOWED);
        }

        payment.retry();

        log.info(
                "payment attempt retried: paymentId={}, attemptNo={}, providerOrderId={}",
                payment.getId(),
                payment.getAttemptNo(),
                payment.getProviderOrderId());
        return PaymentResponse.from(payment);
    }

    /**
     * Toss 결제창에서 승인된 결제를 서버 승인 API로 확정한다. orderId·금액은 클라이언트 응답을
     * 신뢰하지 않고 우리가 저장해둔 값과 대조하고, 같은 시도에 대해서는 항상 같은 멱등키
     * (payment-confirm-{paymentId}-{attemptNo})로 Toss confirm을 호출해 중복 승인을 막는다.
     *
     * <p>Toss 호출은 의도적으로 DB 트랜잭션 밖에서 실행한다 — 외부 호출을 트랜잭션 안에 두면 Toss는
     * 승인했는데 뒤이은 로컬 커밋만 실패하는 불일치가 생길 수 있다. 검증 조회, 실패 기록, 승인 반영은
     * 각각 독립된 트랜잭션으로 나눠 커밋하고, 매물 반영 단계는 별도로 실패를 허용해 Toss가 이미
     * 승인한 결제 자체는 절대 유실하지 않는다.
     */
    public PaymentResponse confirm(Long buyerId, Long paymentId, ConfirmPaymentRequest request) {
        Payment payment = requireConfirmablePayment(buyerId, paymentId, request);

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
            log.info(
                    "payment confirm rejected: paymentId={}, tossCode={}", paymentId, exception.getTossCode());
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_REJECTED, exception.getTossMessage());
        }

        Long listingId = approvePayment(paymentId, tossResponse);
        markListingPaidOrEscalate(listingId, buyerId, paymentId);

        log.info(
                "payment confirmed: paymentId={}, listingId={}, buyerId={}", paymentId, listingId, buyerId);
        return PaymentResponse.from(
                paymentRepository
                        .findById(paymentId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND)));
    }

    private Payment requireConfirmablePayment(Long buyerId, Long paymentId, ConfirmPaymentRequest request) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getBuyer().getId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }
        if (!request.getOrderId().equals(payment.getProviderOrderId())) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_ID_MISMATCH);
        }
        if (payment.getRequestedAmount().compareTo(BigDecimal.valueOf(request.getAmount())) != 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        if (payment.getStatus() != PaymentStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_CONFIRMABLE);
        }
        if (!listingService.isReservationActive(payment.getListingId(), buyerId)) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_RESERVATION_EXPIRED);
        }
        return payment;
    }

    private void recordFailure(Long paymentId, String reason) {
        transactionTemplate.executeWithoutResult(status -> paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND))
                .fail(reason));
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
     */
    private void markListingPaidOrEscalate(Long listingId, Long buyerId, Long paymentId) {
        try {
            listingService.markPaid(listingId, buyerId);
        } catch (RuntimeException exception) {
            log.error(
                    "payment approved by PG but listing could not be marked paid, needs manual "
                            + "reconciliation: paymentId={}, listingId={}, buyerId={}",
                    paymentId,
                    listingId,
                    buyerId,
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
