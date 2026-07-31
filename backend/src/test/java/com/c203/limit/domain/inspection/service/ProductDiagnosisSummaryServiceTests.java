package com.c203.limit.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.dto.response.DiagnosisSummaryItem;
import com.c203.limit.domain.inspection.dto.response.ProductDiagnosisSummaryResponse;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.DiagnosisFieldName;
import com.c203.limit.domain.inspection.enums.DiagnosisSourceType;
import com.c203.limit.domain.inspection.enums.DiagnosisSummaryStatus;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
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
class ProductDiagnosisSummaryServiceTests {

    private static final Long PRODUCT_ID = 1L;
    private static final Long SELLER_ID = 100L;
    private static final Long ITEM_ID = 2L;
    private static final DiagnosisAggregationService.DiagnosisFieldValue EMPTY =
            new DiagnosisAggregationService.DiagnosisFieldValue(null, null, null, null);

    @Mock ListingOwnerReader listingOwnerReader;
    @Mock ListingChecklistItemRepository listingChecklistItemRepository;
    @Mock DiagnosisAggregationService diagnosisAggregationService;
    @Mock EvidenceRepository evidenceRepository;

    ProductDiagnosisSummaryService service;

    @BeforeEach
    void setUp() {
        service =
                new ProductDiagnosisSummaryService(
                        listingOwnerReader, listingChecklistItemRepository, diagnosisAggregationService, evidenceRepository);
    }

    private void stubProductExists() {
        when(listingOwnerReader.findById(PRODUCT_ID))
                .thenReturn(Optional.of(new ListingOwnerReader.ListingOwnerInfo(PRODUCT_ID, SELLER_ID)));
    }

    private DiagnosisSummaryItem fieldNamed(ProductDiagnosisSummaryResponse response, String fieldName) {
        return response.items().stream()
                .filter(item -> item.fieldName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("field not found: " + fieldName));
    }

    @Test
    void mixesAvailableAndExtractionFailedFields() {
        stubProductExists();
        ListingChecklistItem item = mock(ListingChecklistItem.class);
        when(item.getId()).thenReturn(ITEM_ID);
        when(listingChecklistItemRepository.findAllByListingIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(item));

        when(diagnosisAggregationService.getFieldValue(eq(ITEM_ID), any(DiagnosisFieldName.class))).thenReturn(EMPTY);
        when(diagnosisAggregationService.getFieldValue(ITEM_ID, DiagnosisFieldName.CPU))
                .thenReturn(
                        new DiagnosisAggregationService.DiagnosisFieldValue(
                                "ocr cpu", "file cpu", 5L, DiagnosisSourceType.DXDIAG));

        when(evidenceRepository.findById(5L)).thenReturn(Optional.of(readyEvidence("https://cdn.example.com/evidence/5.txt")));

        ProductDiagnosisSummaryResponse response = service.getSummary(PRODUCT_ID);

        assertThat(response.productId()).isEqualTo(PRODUCT_ID);
        assertThat(response.disclaimer())
                .isEqualTo("자동 추출값은 참고 정보이며 상품의 정상 여부를 보증하지 않습니다.");
        assertThat(response.items()).hasSize(DiagnosisFieldName.values().length);

        DiagnosisSummaryItem cpu = fieldNamed(response, "CPU");
        assertThat(cpu.value()).isEqualTo("file cpu");
        assertThat(cpu.status()).isEqualTo(DiagnosisSummaryStatus.AVAILABLE);
        assertThat(cpu.originalFileUrl()).isEqualTo("https://cdn.example.com/evidence/5.txt");

        DiagnosisSummaryItem gpu = fieldNamed(response, "GPU");
        assertThat(gpu.value()).isNull();
        assertThat(gpu.originalFileUrl()).isNull();
        assertThat(gpu.status()).isEqualTo(DiagnosisSummaryStatus.EXTRACTION_FAILED);
    }

    @Test
    void dedupesSameFieldAcrossMultipleChecklistItemsPreferringAvailable() {
        stubProductExists();
        Long ocrItemId = 3L;
        Long dxdiagItemId = 4L;
        ListingChecklistItem ocrItem = mock(ListingChecklistItem.class);
        when(ocrItem.getId()).thenReturn(ocrItemId);
        ListingChecklistItem dxdiagItem = mock(ListingChecklistItem.class);
        when(dxdiagItem.getId()).thenReturn(dxdiagItemId);
        when(listingChecklistItemRepository.findAllByListingIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(ocrItem, dxdiagItem));

        when(diagnosisAggregationService.getFieldValue(eq(ocrItemId), any(DiagnosisFieldName.class)))
                .thenReturn(EMPTY);
        when(diagnosisAggregationService.getFieldValue(eq(dxdiagItemId), any(DiagnosisFieldName.class)))
                .thenReturn(EMPTY);
        // OCR 항목은 CPU 인식에 실패했고, DXDIAG 항목이 대신 CPU 값을 갖고 있는 상황.
        when(diagnosisAggregationService.getFieldValue(dxdiagItemId, DiagnosisFieldName.CPU))
                .thenReturn(
                        new DiagnosisAggregationService.DiagnosisFieldValue(
                                null, "dxdiag cpu", 7L, DiagnosisSourceType.DXDIAG));
        when(evidenceRepository.findById(7L))
                .thenReturn(Optional.of(readyEvidence("https://cdn.example.com/evidence/7.txt")));

        ProductDiagnosisSummaryResponse response = service.getSummary(PRODUCT_ID);

        assertThat(response.items()).hasSize(DiagnosisFieldName.values().length);
        DiagnosisSummaryItem cpu = fieldNamed(response, "CPU");
        assertThat(cpu.status()).isEqualTo(DiagnosisSummaryStatus.AVAILABLE);
        assertThat(cpu.value()).isEqualTo("dxdiag cpu");
        assertThat(cpu.originalFileUrl()).isEqualTo("https://cdn.example.com/evidence/7.txt");
    }

    @Test
    void throwsProductNotFoundWhenListingDoesNotExist() {
        when(listingOwnerReader.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSummary(PRODUCT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private Evidence readyEvidence(String cdnUrl) {
        Evidence evidence =
                Evidence.upload(
                        PRODUCT_ID, null, EvidenceType.DIAGNOSTIC_FILE, "s3/key", "text/plain", LocalDateTime.now());
        evidence.markReady(cdnUrl);
        return evidence;
    }
}
