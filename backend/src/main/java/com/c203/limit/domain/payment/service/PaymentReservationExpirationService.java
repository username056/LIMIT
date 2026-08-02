package com.c203.limit.domain.payment.service;

import com.c203.limit.domain.payment.dto.response.PaymentReconcileOutcome;
import com.c203.limit.domain.payment.dto.response.PaymentReconcileResponse;
import com.c203.limit.domain.payment.entity.Payment;
import com.c203.limit.domain.payment.entity.PaymentStatus;
import com.c203.limit.domain.payment.repository.PaymentRepository;
import com.c203.limit.domain.product.service.ListingService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 예약(RESERVED) 유예 시간 안에 결제가 완료되지 않은 매물을 정리한다. 대상 1건마다 별도
 * 트랜잭션으로 처리해, 한 건의 락 경합·상태 불일치가 나머지 처리를 막지 않게 한다.
 */
@Service
public class PaymentReservationExpirationService {
    private static final Logger log = LoggerFactory.getLogger(PaymentReservationExpirationService.class);
    private static final String EXPIRE_REASON = "예약 유예 시간 안에 결제가 완료되지 않아 자동 만료";

    private final PaymentRepository paymentRepository;
    private final ListingService listingService;
    private final PaymentService paymentService;
    private final TransactionTemplate transactionTemplate;

    public PaymentReservationExpirationService(
            PaymentRepository paymentRepository,
            ListingService listingService,
            PaymentService paymentService,
            PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.listingService = listingService;
        this.paymentService = paymentService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 대상 매물 하나를 만료 처리한다. confirm을 실제로 시도한 적 있는 REQUESTED 결제
     * ({@link Payment#getConfirmAttemptedAt()}가 채워진 건)는, 서버가 Toss confirm을 불렀지만
     * 응답을 못 받았을 뿐 실제로는 승인됐을 수 있다 — 이런 건을 시간만 보고 바로 만료시키면 돈은
     * 빠져나갔는데 매물을 재오픈하는 사고로 이어진다. 그래서 만료 전에 {@link PaymentService#reconcile}
     * 로 PG 상태를 한 번 재확인하고, 복구되면 만료시키지 않는다. confirm을 시도한 적 없는 순수 이탈
     * 건은 이 조회 없이 기존대로 즉시 만료한다.
     *
     * <p>이미 다른 실행으로 상태가 바뀐 경우(중복 실행, 동시 처리 등)에는
     * {@link com.c203.limit.global.exception.BusinessException}이 그대로 전파되어 트랜잭션이
     * 롤백된다 — 호출자(스케줄러)가 건마다 잡아서 나머지 처리를 이어간다.
     */
    public ReservationExpirationResult expireOne(Long listingId) {
        Optional<Payment> payment =
                paymentRepository.findByListingIdAndStatus(listingId, PaymentStatus.REQUESTED);
        if (payment.isEmpty()) {
            log.warn(
                    "reservation expiration skipped: no requested payment found for listingId={}",
                    listingId);
            return ReservationExpirationResult.SKIPPED_NO_PAYMENT;
        }

        if (payment.get().getConfirmAttemptedAt() != null) {
            PaymentReconcileResponse reconciled = paymentService.reconcile(payment.get().getId());
            if (reconciled.getOutcome() == PaymentReconcileOutcome.RECOVERED) {
                log.info(
                        "reservation expiration skipped: payment recovered via reconcile before "
                                + "expiry, listingId={}, paymentId={}",
                        listingId,
                        payment.get().getId());
                return ReservationExpirationResult.RECOVERED;
            }
        }

        return expireInTransaction(listingId);
    }

    private ReservationExpirationResult expireInTransaction(Long listingId) {
        Long expiredPaymentId = transactionTemplate.execute(status -> {
            Optional<Payment> payment =
                    paymentRepository.findByListingIdAndStatus(listingId, PaymentStatus.REQUESTED);
            if (payment.isEmpty()) {
                return null;
            }
            payment.get().expire(EXPIRE_REASON);
            listingService.expireReservation(listingId, EXPIRE_REASON);
            return payment.get().getId();
        });

        if (expiredPaymentId == null) {
            log.warn(
                    "reservation expiration skipped: requested payment no longer present for "
                            + "listingId={}",
                    listingId);
            return ReservationExpirationResult.SKIPPED_NO_PAYMENT;
        }

        log.info(
                "payment reservation expired: paymentId={}, listingId={}", expiredPaymentId, listingId);
        return ReservationExpirationResult.EXPIRED;
    }
}
