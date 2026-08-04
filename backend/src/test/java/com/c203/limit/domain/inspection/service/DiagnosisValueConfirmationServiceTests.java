package com.c203.limit.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.dto.request.DiagnosisValueUpdateRequest;
import com.c203.limit.domain.inspection.dto.response.DiagnosisValueUpdateResponse;
import com.c203.limit.domain.inspection.entity.BatteryReportResult;
import com.c203.limit.domain.inspection.entity.DxdiagResult;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.entity.OcrResult;
import com.c203.limit.domain.inspection.enums.DiagnosisFieldName;
import com.c203.limit.domain.inspection.enums.DiagnosisSourceType;
import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import com.c203.limit.domain.inspection.repository.BatteryReportResultRepository;
import com.c203.limit.domain.inspection.repository.DxdiagResultRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.domain.inspection.repository.OcrResultRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiagnosisValueConfirmationServiceTests {

    private static final Long ITEM_ID = 2L;
    private static final Long LISTING_ID = 10L;
    private static final Long SELLER_ID = 100L;
    private static final Long OTHER_MEMBER_ID = 200L;
    private static final Long EVIDENCE_ID = 5L;

    @Mock ListingChecklistItemRepository listingChecklistItemRepository;
    @Mock ListingOwnerReader listingOwnerReader;
    @Mock DiagnosisAggregationService diagnosisAggregationService;
    @Mock OcrResultRepository ocrResultRepository;
    @Mock DxdiagResultRepository dxdiagResultRepository;
    @Mock BatteryReportResultRepository batteryReportResultRepository;
    @Mock ListingChecklistItem listingChecklistItem;

    DiagnosisValueConfirmationService service;

    @BeforeEach
    void setUp() {
        service =
                new DiagnosisValueConfirmationService(
                        listingChecklistItemRepository,
                        listingOwnerReader,
                        diagnosisAggregationService,
                        ocrResultRepository,
                        dxdiagResultRepository,
                        batteryReportResultRepository);
    }

    private void stubItemAndOwnership() {
        when(listingChecklistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(listingChecklistItem));
        when(listingChecklistItem.getListingId()).thenReturn(LISTING_ID);
        when(listingOwnerReader.findById(LISTING_ID))
                .thenReturn(Optional.of(new ListingOwnerReader.ListingOwnerInfo(LISTING_ID, SELLER_ID)));
    }

    @Test
    void correctsDxdiagResultColumnInPlaceWhenSourceIsDxdiag() {
        stubItemAndOwnership();
        when(diagnosisAggregationService.getFieldValue(ITEM_ID, DiagnosisFieldName.CPU))
                .thenReturn(
                        new DiagnosisAggregationService.DiagnosisFieldValue(
                                "ocr cpu", "file cpu", EVIDENCE_ID, DiagnosisSourceType.DXDIAG));
        DxdiagResult existing =
                DxdiagResult.builder()
                        .evidenceId(EVIDENCE_ID)
                        .cpu("file cpu")
                        .parserVersion("dxdiag-v1")
                        .parseStatus(ParseStatus.SUCCESS)
                        .parsedAt(LocalDateTime.now())
                        .build();
        when(dxdiagResultRepository.findByEvidenceId(EVIDENCE_ID)).thenReturn(Optional.of(existing));

        DiagnosisValueUpdateRequest request = new DiagnosisValueUpdateRequest("CPU", "corrected cpu");
        DiagnosisValueUpdateResponse response = service.confirm(ITEM_ID, SELLER_ID, request);

        assertThat(existing.getCpu()).isEqualTo("corrected cpu");
        assertThat(response.getOriginalValue()).isEqualTo("file cpu");
        assertThat(response.getConfirmedValue()).isEqualTo("corrected cpu");
    }

    @Test
    void correctsOcrResultInPlaceWhenSourceIsOcr() {
        stubItemAndOwnership();
        when(diagnosisAggregationService.getFieldValue(ITEM_ID, DiagnosisFieldName.MODEL_NAME))
                .thenReturn(
                        new DiagnosisAggregationService.DiagnosisFieldValue(
                                "Galaxy Book4", null, EVIDENCE_ID, DiagnosisSourceType.OCR));
        OcrResult existing =
                OcrResult.builder()
                        .evidenceId(EVIDENCE_ID)
                        .fieldType(OcrFieldType.MODEL_NAME)
                        .parsedValue("Galaxy Book4")
                        .ocrModelVersion("mock-v1")
                        .detectedAt(LocalDateTime.now())
                        .build();
        when(ocrResultRepository.findByEvidenceIdAndFieldType(EVIDENCE_ID, OcrFieldType.MODEL_NAME))
                .thenReturn(Optional.of(existing));

        DiagnosisValueUpdateRequest request = new DiagnosisValueUpdateRequest("MODEL_NAME", "Galaxy Book4 Pro");
        DiagnosisValueUpdateResponse response = service.confirm(ITEM_ID, SELLER_ID, request);

        assertThat(existing.getParsedValue()).isEqualTo("Galaxy Book4 Pro");
        assertThat(response.getOriginalValue()).isEqualTo("Galaxy Book4");
        assertThat(response.getConfirmedValue()).isEqualTo("Galaxy Book4 Pro");
    }

    @Test
    void correctsBatteryReportResultInPlaceWhenSourceIsBatteryReport() {
        stubItemAndOwnership();
        when(diagnosisAggregationService.getFieldValue(
                        ITEM_ID, DiagnosisFieldName.CYCLE_COUNT))
                .thenReturn(
                        new DiagnosisAggregationService.DiagnosisFieldValue(
                                null, "288", EVIDENCE_ID, DiagnosisSourceType.BATTERY_REPORT));
        BatteryReportResult existing =
                BatteryReportResult.builder()
                        .evidenceId(EVIDENCE_ID)
                        .cycleCount(288)
                        .parserVersion("battery-report-v1")
                        .parseStatus(ParseStatus.SUCCESS)
                        .parsedAt(LocalDateTime.now())
                        .build();
        when(batteryReportResultRepository.findByEvidenceId(EVIDENCE_ID)).thenReturn(Optional.of(existing));

        DiagnosisValueUpdateRequest request = new DiagnosisValueUpdateRequest("CYCLE_COUNT", "300");
        DiagnosisValueUpdateResponse response = service.confirm(ITEM_ID, SELLER_ID, request);

        assertThat(existing.getCycleCount()).isEqualTo(300);
        assertThat(response.getOriginalValue()).isEqualTo("288");
        assertThat(response.getConfirmedValue()).isEqualTo("300");
    }

    @Test
    void createsNewOcrResultRowWhenFieldWasNeverDetected() {
        stubItemAndOwnership();
        when(diagnosisAggregationService.getFieldValue(ITEM_ID, DiagnosisFieldName.MODEL_NAME))
                .thenReturn(
                        new DiagnosisAggregationService.DiagnosisFieldValue(
                                null, null, EVIDENCE_ID, DiagnosisSourceType.OCR));
        when(ocrResultRepository.findByEvidenceIdAndFieldType(EVIDENCE_ID, OcrFieldType.MODEL_NAME))
                .thenReturn(Optional.empty());

        DiagnosisValueUpdateRequest request = new DiagnosisValueUpdateRequest("MODEL_NAME", "Galaxy Book4 Pro");
        DiagnosisValueUpdateResponse response = service.confirm(ITEM_ID, SELLER_ID, request);

        assertThat(response.getOriginalValue()).isNull();
        assertThat(response.getConfirmedValue()).isEqualTo("Galaxy Book4 Pro");
        org.mockito.ArgumentCaptor<OcrResult> captor = org.mockito.ArgumentCaptor.forClass(OcrResult.class);
        org.mockito.Mockito.verify(ocrResultRepository).save(captor.capture());
        assertThat(captor.getValue().getParsedValue()).isEqualTo("Galaxy Book4 Pro");
        assertThat(captor.getValue().getEvidenceId()).isEqualTo(EVIDENCE_ID);
    }

    @Test
    void createsNewDxdiagResultRowWhenParsingRowIsMissing() {
        stubItemAndOwnership();
        when(diagnosisAggregationService.getFieldValue(ITEM_ID, DiagnosisFieldName.SOUND_DEVICE))
                .thenReturn(
                        new DiagnosisAggregationService.DiagnosisFieldValue(
                                null, null, EVIDENCE_ID, DiagnosisSourceType.DXDIAG));
        when(dxdiagResultRepository.findByEvidenceId(EVIDENCE_ID)).thenReturn(Optional.empty());

        DiagnosisValueUpdateRequest request = new DiagnosisValueUpdateRequest("SOUND_DEVICE", "Realtek Audio");
        service.confirm(ITEM_ID, SELLER_ID, request);

        org.mockito.ArgumentCaptor<DxdiagResult> captor = org.mockito.ArgumentCaptor.forClass(DxdiagResult.class);
        org.mockito.Mockito.verify(dxdiagResultRepository).save(captor.capture());
        assertThat(captor.getValue().getSoundDevice()).isEqualTo("Realtek Audio");
        assertThat(captor.getValue().getEvidenceId()).isEqualTo(EVIDENCE_ID);
    }

    @Test
    void createsNewBatteryReportResultRowWhenParsingRowIsMissing() {
        stubItemAndOwnership();
        when(diagnosisAggregationService.getFieldValue(ITEM_ID, DiagnosisFieldName.BATTERY_MANUFACTURER))
                .thenReturn(
                        new DiagnosisAggregationService.DiagnosisFieldValue(
                                null, null, EVIDENCE_ID, DiagnosisSourceType.BATTERY_REPORT));
        when(batteryReportResultRepository.findByEvidenceId(EVIDENCE_ID)).thenReturn(Optional.empty());

        DiagnosisValueUpdateRequest request =
                new DiagnosisValueUpdateRequest("BATTERY_MANUFACTURER", "LG Chem");
        service.confirm(ITEM_ID, SELLER_ID, request);

        org.mockito.ArgumentCaptor<BatteryReportResult> captor =
                org.mockito.ArgumentCaptor.forClass(BatteryReportResult.class);
        org.mockito.Mockito.verify(batteryReportResultRepository).save(captor.capture());
        assertThat(captor.getValue().getBatteryManufacturer()).isEqualTo("LG Chem");
        assertThat(captor.getValue().getEvidenceId()).isEqualTo(EVIDENCE_ID);
    }

    @Test
    void throwsFieldNotEditableWhenNoAutoExtractedSourceExists() {
        stubItemAndOwnership();
        when(diagnosisAggregationService.getFieldValue(ITEM_ID, DiagnosisFieldName.GPU))
                .thenReturn(new DiagnosisAggregationService.DiagnosisFieldValue(null, null, null, null));

        DiagnosisValueUpdateRequest request = new DiagnosisValueUpdateRequest("GPU", "some value");

        assertThatThrownBy(() -> service.confirm(ITEM_ID, SELLER_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FIELD_NOT_EDITABLE));
        verifyNoInteractions(ocrResultRepository, dxdiagResultRepository, batteryReportResultRepository);
    }

    @Test
    void savesDeviceInfoValueWithoutEvidence() {
        stubItemAndOwnership();
        when(listingChecklistItem.getItemCode()).thenReturn("LAP-SCR-013");
        when(diagnosisAggregationService.getFieldValue(ITEM_ID, DiagnosisFieldName.MODEL_NAME))
                .thenReturn(new DiagnosisAggregationService.DiagnosisFieldValue(null, null, null, null));

        DiagnosisValueUpdateResponse response =
                service.confirm(
                        ITEM_ID,
                        SELLER_ID,
                        new DiagnosisValueUpdateRequest("MODEL_NAME", "Galaxy Book4 Ultra"));

        org.mockito.Mockito.verify(listingChecklistItem)
                .correctManualDeviceInfo(DiagnosisFieldName.MODEL_NAME, "Galaxy Book4 Ultra");
        org.mockito.Mockito.verify(listingChecklistItemRepository).save(listingChecklistItem);
        assertThat(response.getConfirmedValue()).isEqualTo("Galaxy Book4 Ultra");
    }

    @Test
    void throwsFieldNotEditableForUnknownFieldName() {
        stubItemAndOwnership();
        DiagnosisValueUpdateRequest request = new DiagnosisValueUpdateRequest("NOT_A_REAL_FIELD", "value");

        assertThatThrownBy(() -> service.confirm(ITEM_ID, SELLER_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FIELD_NOT_EDITABLE));
        verifyNoInteractions(diagnosisAggregationService, ocrResultRepository, dxdiagResultRepository, batteryReportResultRepository);
    }

    @Test
    void throwsWhenItemNotFound() {
        when(listingChecklistItemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());
        DiagnosisValueUpdateRequest request = new DiagnosisValueUpdateRequest("CPU", "value");

        assertThatThrownBy(() -> service.confirm(ITEM_ID, SELLER_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ITEM_NOT_FOUND));
    }

    @Test
    void throwsForbiddenWhenSellerDoesNotOwnListing() {
        stubItemAndOwnership();
        DiagnosisValueUpdateRequest request = new DiagnosisValueUpdateRequest("CPU", "value");

        assertThatThrownBy(() -> service.confirm(ITEM_ID, OTHER_MEMBER_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(diagnosisAggregationService, ocrResultRepository, dxdiagResultRepository, batteryReportResultRepository);
    }
}
