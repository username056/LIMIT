package com.c203.limit.domain.payment.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.payment.repository.ExpiredReservationCandidateReader;
import com.c203.limit.domain.payment.service.PaymentReservationExpirationService;
import com.c203.limit.domain.payment.service.ReservationExpirationResult;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentReservationExpirationSchedulerTests {

    @Mock ExpiredReservationCandidateReader candidateReader;
    @Mock PaymentReservationExpirationService expirationService;

    PaymentReservationExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler =
                new PaymentReservationExpirationScheduler(
                        candidateReader, expirationService, Clock.systemDefaultZone());
    }

    @Test
    void expireOverdueReservationsDoesNothingWhenNoTargets() {
        when(candidateReader.findExpiredListingIds(any(), anyInt())).thenReturn(List.of());

        scheduler.expireOverdueReservations();

        verify(expirationService, times(0)).expireOne(any());
    }

    @Test
    void expireOverdueReservationsProcessesEachTargetIndependently() {
        when(candidateReader.findExpiredListingIds(any(), anyInt())).thenReturn(List.of(1L, 2L, 3L, 4L));
        doThrow(new BusinessException(ErrorCode.PAYMENT_NOT_EXPIRABLE))
                .when(expirationService)
                .expireOne(1L);
        when(expirationService.expireOne(2L)).thenReturn(ReservationExpirationResult.EXPIRED);
        when(expirationService.expireOne(3L))
                .thenReturn(ReservationExpirationResult.SKIPPED_NO_PAYMENT);
        when(expirationService.expireOne(4L)).thenReturn(ReservationExpirationResult.RECOVERED);

        scheduler.expireOverdueReservations();

        verify(expirationService).expireOne(1L);
        verify(expirationService).expireOne(2L);
        verify(expirationService).expireOne(eq(3L));
        verify(expirationService).expireOne(eq(4L));
    }
}
