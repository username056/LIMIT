package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductChecklistServiceTests {

    @Mock ListingRepository listingRepository;
    @Mock ListingChecklistItemRepository checklistItemRepository;
    @Mock EvidenceRepository evidenceRepository;
    ProductChecklistService service;

    @BeforeEach
    void setUp() {
        service = new ProductChecklistService(
                listingRepository, checklistItemRepository, evidenceRepository);
    }

    @Test
    void returnsListingSnapshotIncludingSelectedFeatureGuide() {
        ListingChecklistItem camera = checklistItem(
                7003L,
                "LAP-FTR-CAM",
                "내장 카메라",
                "카메라 앱을 실행해 영상 출력 상태를 확인하세요.",
                false,
                ChecklistItemCompletionStatus.PENDING);
        Evidence first = mock(Evidence.class);
        when(first.getUploadedAt()).thenReturn(LocalDateTime.of(2026, 7, 29, 10, 0));
        when(first.getListingChecklistItem()).thenReturn(camera);
        Evidence latest =
                evidence(9002L, LocalDateTime.of(2026, 7, 29, 10, 5), camera);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(mock(Listing.class)));
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of(camera));
        when(evidenceRepository.findAllByListingId(1001L))
                .thenReturn(List.of(first, latest));

        var result = service.findAll(1001L, null, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getItemCode()).isEqualTo("LAP-FTR-CAM");
        assertThat(result.get(0).getGuide())
                .isEqualTo("카메라 앱을 실행해 영상 출력 상태를 확인하세요.");
        assertThat(result.get(0).getLatestEvidenceId()).isEqualTo(9002L);
        assertThat(result.get(0).getAttemptCount()).isEqualTo(2);
        verify(evidenceRepository).findAllByListingId(1001L);
    }

    @Test
    void supportsRequiredAndStatusFilters() {
        ListingChecklistItem completedHinge = checklistItem(
                7001L,
                "LAP-HNG-012",
                "힌지 상태",
                "화면을 천천히 여닫아 유격과 소음을 확인하세요.",
                true,
                ChecklistItemCompletionStatus.COMPLETED);
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(mock(Listing.class)));
        when(checklistItemRepository
                        .findByListingIdAndIsRequiredTrueOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of(completedHinge));
        when(evidenceRepository.findAllByListingId(1001L))
                .thenReturn(List.of());

        var result = service.findAll(1001L, "completed", true);

        assertThat(result).extracting("itemCode").containsExactly("LAP-HNG-012");
        verify(checklistItemRepository)
                .findByListingIdAndIsRequiredTrueOrderByDisplayOrderAsc(1001L);
    }

    @Test
    void rejectsUnknownStatus() {
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.of(mock(Listing.class)));

        assertThatThrownBy(() -> service.findAll(1001L, "UNKNOWN", false))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void rejectsMissingProduct() {
        when(listingRepository.findByIdAndDeletedAtIsNull(1001L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findAll(1001L, null, false))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private ListingChecklistItem checklistItem(
            Long id,
            String itemCode,
            String name,
            String guide,
            boolean required,
            ChecklistItemCompletionStatus status) {
        ListingChecklistItem item = mock(ListingChecklistItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getItemCode()).thenReturn(itemCode);
        when(item.getName()).thenReturn(name);
        when(item.getCaptureGuide()).thenReturn(guide);
        when(item.getEvidenceType()).thenReturn(EvidenceType.VIDEO);
        when(item.getAutomationType()).thenReturn(AutomationType.NONE);
        when(item.isRequired()).thenReturn(required);
        when(item.getCompletionStatus()).thenReturn(status);
        return item;
    }

    private Evidence evidence(
            Long id, LocalDateTime uploadedAt, ListingChecklistItem checklistItem) {
        Evidence evidence = mock(Evidence.class);
        when(evidence.getId()).thenReturn(id);
        when(evidence.getUploadedAt()).thenReturn(uploadedAt);
        when(evidence.getListingChecklistItem()).thenReturn(checklistItem);
        return evidence;
    }
}
