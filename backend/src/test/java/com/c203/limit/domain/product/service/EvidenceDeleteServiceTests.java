package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.repository.BatteryReportResultRepository;
import com.c203.limit.domain.inspection.repository.DxdiagResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.OcrResultRepository;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.storage.S3MediaProperties;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvidenceDeleteServiceTests {

    @Mock ListingRepository listingRepository;
    @Mock ListingChecklistItemRepository checklistItemRepository;
    @Mock EvidenceRepository evidenceRepository;
    @Mock BatteryReportResultRepository batteryReportResultRepository;
    @Mock DxdiagResultRepository dxdiagResultRepository;
    @Mock OcrResultRepository ocrResultRepository;
    @Mock ApplicationEventPublisher events;
    EvidenceDeleteService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceDeleteService(
                listingRepository,
                checklistItemRepository,
                evidenceRepository,
                batteryReportResultRepository,
                dxdiagResultRepository,
                ocrResultRepository,
                new S3MediaProperties(
                        "ap-northeast-2",
                        "l1mit-dev-media-0b849303",
                        null,
                        false,
                        Duration.ofMinutes(10),
                        Duration.ofMinutes(5),
                        ""),
                events);
    }

    @Test
    void deletesEvidenceAndCleansUpParseResultsAndRevertsPendingWhenBelowMinCount() {
        Listing listing = listing(1001L, 55L);
        ListingChecklistItem item = item(7002L, 1);
        Evidence evidence = evidence(9001L, 7002L, "evidence/1001/7002/file.html");
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L)).thenReturn(Optional.of(listing));
        when(checklistItemRepository.findByIdAndListingId(7002L, 1001L))
                .thenReturn(Optional.of(item));
        when(evidenceRepository.findByIdAndListingId(9001L, 1001L))
                .thenReturn(Optional.of(evidence));
        when(evidenceRepository.countByListingChecklistItem_Id(7002L)).thenReturn(0L);

        service.delete(55L, 1001L, 7002L, 9001L);

        verify(batteryReportResultRepository).deleteByEvidenceId(9001L);
        verify(dxdiagResultRepository).deleteByEvidenceId(9001L);
        verify(ocrResultRepository).deleteByEvidenceId(9001L);
        verify(evidenceRepository).delete(evidence);
        verify(item).markPending();
        verify(events)
                .publishEvent(new EvidenceDeletedEvent(
                        "l1mit-dev-media-0b849303", "evidence/1001/7002/file.html"));
    }

    @Test
    void keepsCompletionStatusWhenEnoughEvidenceRemains() {
        Listing listing = listing(1001L, 55L);
        ListingChecklistItem item = item(7002L, 1);
        Evidence evidence = evidence(9001L, 7002L, "evidence/1001/7002/file.html");
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L)).thenReturn(Optional.of(listing));
        when(checklistItemRepository.findByIdAndListingId(7002L, 1001L))
                .thenReturn(Optional.of(item));
        when(evidenceRepository.findByIdAndListingId(9001L, 1001L))
                .thenReturn(Optional.of(evidence));
        when(evidenceRepository.countByListingChecklistItem_Id(7002L)).thenReturn(1L);

        service.delete(55L, 1001L, 7002L, 9001L);

        verify(item, never()).markPending();
    }

    @Test
    void rejectsWhenEvidenceBelongsToDifferentChecklistItem() {
        Listing listing = listing(1001L, 55L);
        ListingChecklistItem item = item(7002L, 1);
        Evidence evidence = evidence(9001L, 9999L, "evidence/1001/9999/file.html");
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L)).thenReturn(Optional.of(listing));
        when(checklistItemRepository.findByIdAndListingId(7002L, 1001L))
                .thenReturn(Optional.of(item));
        when(evidenceRepository.findByIdAndListingId(9001L, 1001L))
                .thenReturn(Optional.of(evidence));

        assertThatThrownBy(() -> service.delete(55L, 1001L, 7002L, 9001L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EVIDENCE_NOT_FOUND));
        verify(evidenceRepository, never()).delete(any());
    }

    @Test
    void rejectsWhenSellerDoesNotOwnListing() {
        Listing listing = listing(1001L, 55L);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.delete(999L, 1001L, 7002L, 9001L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_ACCESS_DENIED));
        verify(evidenceRepository, never()).delete(any());
    }

    private Listing listing(Long id, Long sellerId) {
        Listing listing = org.mockito.Mockito.mock(Listing.class);
        when(listing.getId()).thenReturn(id);
        when(listing.getSellerId()).thenReturn(sellerId);
        return listing;
    }

    private ListingChecklistItem item(Long id, Integer minCount) {
        ListingChecklistItem item = org.mockito.Mockito.mock(ListingChecklistItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getMinCount()).thenReturn(minCount);
        return item;
    }

    private Evidence evidence(Long id, Long checklistItemId, String s3Key) {
        Evidence evidence = org.mockito.Mockito.mock(Evidence.class);
        ListingChecklistItem owningItem = org.mockito.Mockito.mock(ListingChecklistItem.class);
        when(owningItem.getId()).thenReturn(checklistItemId);
        when(evidence.getId()).thenReturn(id);
        when(evidence.getListingChecklistItem()).thenReturn(owningItem);
        when(evidence.getS3Key()).thenReturn(s3Key);
        return evidence;
    }
}
