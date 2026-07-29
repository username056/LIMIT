package com.c203.limit.domain.payment.service;

import com.c203.limit.domain.payment.entity.Payment;
import com.c203.limit.domain.payment.entity.PaymentStatus;
import com.c203.limit.domain.payment.repository.PaymentRepository;
import com.c203.limit.domain.product.service.ListingService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public PaymentReservationExpirationService(
            PaymentRepository paymentRepository, ListingService listingService) {
        this.paymentRepository = paymentRepository;
        this.listingService = listingService;
    }

    /**
     * 대상 매물 하나를 만료 처리한다. 이미 다른 실행으로 상태가 바뀐 경우(중복 실행, 동시 처리
     * 등)에는 {@link com.c203.limit.global.exception.BusinessException}이 그대로 전파되어
     * 트랜잭션이 롤백된다 — 호출자(스케줄러)가 건마다 잡아서 나머지 처리를 이어간다.
     */
    @Transactional
    public ReservationExpirationResult expireOne(Long listingId) {
        Optional<Payment> payment =
                paymentRepository.findByListingIdAndStatus(listingId, PaymentStatus.REQUESTED);
        if (payment.isEmpty()) {
            log.warn(
                    "reservation expiration skipped: no requested payment found for listingId={}",
                    listingId);
            return ReservationExpirationResult.SKIPPED_NO_PAYMENT;
        }

        payment.get().expire(EXPIRE_REASON);
        listingService.expireReservation(listingId, EXPIRE_REASON);

        log.info(
                "payment reservation expired: paymentId={}, listingId={}",
                payment.get().getId(),
                listingId);
        return ReservationExpirationResult.EXPIRED;
    }
}
