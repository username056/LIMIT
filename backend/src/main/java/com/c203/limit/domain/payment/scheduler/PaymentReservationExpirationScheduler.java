package com.c203.limit.domain.payment.scheduler;

import com.c203.limit.domain.payment.repository.ExpiredReservationCandidateReader;
import com.c203.limit.domain.payment.service.PaymentReservationExpirationService;
import com.c203.limit.domain.payment.service.ReservationExpirationResult;
import com.c203.limit.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * reserved_until이 지난 RESERVED 매물을 주기적으로 찾아 만료 처리한다. 한 번에 너무 많은 건을
 * 처리하지 않도록 배치 크기를 제한하고, 건별로 독립 처리해 한 건의 실패가 나머지를 막지 않는다.
 *
 * <p>{@code limit.payment.reservation-expiration.enabled=false}로 끌 수 있다 — 테스트에서
 * {@code @Scheduled} 자동 실행과 직접 호출이 경합하지 않도록 끄거나, Blue/Green 배포에서 특정
 * 인스턴스만 실행하도록 제한할 때 사용한다.
 */
@Component
@ConditionalOnProperty(
        prefix = "limit.payment.reservation-expiration",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PaymentReservationExpirationScheduler {
    private static final Logger log =
            LoggerFactory.getLogger(PaymentReservationExpirationScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final ExpiredReservationCandidateReader candidateReader;
    private final PaymentReservationExpirationService expirationService;
    private final Clock clock;

    public PaymentReservationExpirationScheduler(
            ExpiredReservationCandidateReader candidateReader,
            PaymentReservationExpirationService expirationService,
            Clock clock) {
        this.candidateReader = candidateReader;
        this.expirationService = expirationService;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${limit.payment.reservation-expiration.initial-delay-ms:60000}",
            fixedDelayString = "${limit.payment.reservation-expiration.fixed-delay-ms:60000}")
    public void expireOverdueReservations() {
        List<Long> targets =
                candidateReader.findExpiredListingIds(LocalDateTime.now(clock), BATCH_SIZE);

        if (targets.isEmpty()) {
            return;
        }

        int expired = 0;
        int recovered = 0;
        int skippedNoPayment = 0;
        int failed = 0;
        for (Long listingId : targets) {
            try {
                ReservationExpirationResult result = expirationService.expireOne(listingId);
                if (result == ReservationExpirationResult.EXPIRED) {
                    expired++;
                } else if (result == ReservationExpirationResult.RECOVERED) {
                    recovered++;
                } else {
                    skippedNoPayment++;
                }
            } catch (BusinessException exception) {
                failed++;
                log.warn(
                        "reservation expiration rejected for listingId={}: errorCode={}",
                        listingId,
                        exception.getErrorCode().getCode());
            } catch (RuntimeException exception) {
                failed++;
                log.warn(
                        "reservation expiration failed for listingId={}: {}",
                        listingId,
                        exception.getClass().getSimpleName());
            }
        }

        log.info(
                "reservation expiration batch finished: targets={}, expired={}, recovered={}, "
                        + "skippedNoPayment={}, failed={}",
                targets.size(),
                expired,
                recovered,
                skippedNoPayment,
                failed);
    }
}
