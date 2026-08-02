package com.c203.limit.domain.product.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.service.ListingService;
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
class ListingAutoConfirmSchedulerTests {

    @Mock ListingRepository listingRepository;
    @Mock ListingService listingService;

    ListingAutoConfirmScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler =
                new ListingAutoConfirmScheduler(
                        listingRepository, listingService, Clock.systemDefaultZone());
    }

    @Test
    void autoConfirmOverdueInspectionsDoesNothingWhenNoTargets() {
        when(listingRepository.findAutoConfirmCandidateIds(any(), any())).thenReturn(List.of());

        scheduler.autoConfirmOverdueInspections();

        verify(listingService, times(0)).autoConfirm(any());
    }

    @Test
    void autoConfirmOverdueInspectionsProcessesEachTargetIndependently() {
        when(listingRepository.findAutoConfirmCandidateIds(any(), any()))
                .thenReturn(List.of(1L, 2L, 3L));
        doThrow(new BusinessException(ErrorCode.LISTING_NOT_INSPECTING))
                .when(listingService)
                .autoConfirm(1L);
        doThrow(new RuntimeException("db error")).when(listingService).autoConfirm(2L);

        scheduler.autoConfirmOverdueInspections();

        verify(listingService).autoConfirm(1L);
        verify(listingService).autoConfirm(2L);
        verify(listingService).autoConfirm(3L);
    }
}
