package com.c203.limit.domain.payment.service;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.payment.dto.request.CreatePaymentRequest;
import com.c203.limit.domain.payment.dto.response.PaymentResponse;
import com.c203.limit.domain.payment.entity.Payment;
import com.c203.limit.domain.payment.repository.PaymentRepository;
import com.c203.limit.domain.product.service.ListingReservationView;
import com.c203.limit.domain.product.service.ListingService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
    private static final ZoneId PAYMENT_TIME_ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_CREATE_ATTEMPTS = 3;

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final ListingService listingService;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(
            PaymentRepository paymentRepository,
            MemberRepository memberRepository,
            ListingService listingService,
            PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.memberRepository = memberRepository;
        this.listingService = listingService;
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
        return paymentRepository
                .findByBuyerIdAndIdempotencyKey(buyerId, request.getIdempotencyKey())
                .map(existing -> toResponseOrConflict(existing, request))
                .orElseThrow(() -> new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
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

        log.info(
                "payment requested: paymentId={}, listingId={}, buyerId={}",
                payment.getId(),
                request.getListingId(),
                buyerId);
        return toResponse(payment);
    }

    private PaymentResponse toResponseOrConflict(Payment payment, CreatePaymentRequest request) {
        if (!payment.getListingId().equals(request.getListingId())
                || payment.getMethod() != request.getMethod()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long buyerId, Long paymentId) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getBuyer().getId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.PAYMENT_ACCESS_DENIED);
        }
        return toResponse(payment);
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getListingId(),
                payment.getStatus().name(),
                payment.getMethod() == null ? null : payment.getMethod().name(),
                payment.getAttemptNo(),
                payment.getRequestedAmount(),
                payment.getApprovedAmount(),
                offset(payment.getRequestedAt()),
                offset(payment.getApprovedAt()));
    }

    private OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atZone(PAYMENT_TIME_ZONE).toOffsetDateTime();
    }
}
