package com.c203.limit.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.dto.response.DiagnosisFieldListResponse;
import com.c203.limit.domain.inspection.dto.response.DiagnosisFieldResponse;
import com.c203.limit.domain.inspection.entity.BatteryReportResult;
import com.c203.limit.domain.inspection.entity.DxdiagResult;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.entity.OcrResult;
import com.c203.limit.domain.inspection.enums.DiagnosisFieldName;
import com.c203.limit.domain.inspection.enums.DiagnosisSourceType;
import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import com.c203.limit.domain.inspection.repository.BatteryReportResultRepository;
import com.c203.limit.domain.inspection.repository.DxdiagResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.domain.inspection.repository.OcrResultRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DiagnosisAggregationServiceTests {

    private static final Long ITEM_ID = 1L;
    private static final Long LISTING_ID = 10L;
    private static final Long SELLER_ID = 100L;
    private static final Long OTHER_MEMBER_ID = 200L;
    private static final Long PHOTO_EVIDENCE_ID = 9001L;
    private static final Long DIAGNOSTIC_FILE_EVIDENCE_ID = 9002L;

    @Mock ListingChecklistItemRepository listingChecklistItemRepository;
    @Mock EvidenceRepository evidenceRepository;
    @Mock OcrResultRepository ocrResultRepository;
    @Mock DxdiagResultRepository dxdiagResultRepository;
    @Mock BatteryReportResultRepository batteryReportResultRepository;
    @Mock ListingOwnerReader listingOwnerReader;
    @Mock ListingChecklistItem listingChecklistItem;

    DiagnosisAggregationService service;

    @BeforeEach
    void setUp() {
        lenient().when(listingChecklistItem.getId()).thenReturn(ITEM_ID);
        service =
                new DiagnosisAggregationService(
                        listingChecklistItemRepository,
                        evidenceRepository,
                        ocrResultRepository,
                        dxdiagResultRepository,
                        batteryReportResultRepository,
                        listingOwnerReader);
    }

    private void stubItemAndOwnership() {
        when(listingChecklistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(listingChecklistItem));
        when(listingChecklistItem.getListingId()).thenReturn(LISTING_ID);
        when(listingOwnerReader.findById(LISTING_ID))
                .thenReturn(Optional.of(new ListingOwnerReader.ListingOwnerInfo(LISTING_ID, SELLER_ID)));
    }

    private Evidence photoEvidence() {
        Evidence evidence =
                Evidence.upload(
                        LISTING_ID, listingChecklistItem, EvidenceType.PHOTO, "s3/photo.png", "image/png",
                        LocalDateTime.now());
        ReflectionTestUtils.setField(evidence, "id", PHOTO_EVIDENCE_ID);
        return evidence;
    }

    private Evidence diagnosticFileEvidence() {
        Evidence evidence =
                Evidence.upload(
                        LISTING_ID, listingChecklistItem, EvidenceType.DIAGNOSTIC_FILE, "s3/dxdiag.txt",
                        "text/plain", LocalDateTime.now());
        ReflectionTestUtils.setField(evidence, "id", DIAGNOSTIC_FILE_EVIDENCE_ID);
        return evidence;
    }

    private OcrResult ocrResult(OcrFieldType fieldType, String parsedValue) {
        return OcrResult.builder()
                .evidenceId(PHOTO_EVIDENCE_ID)
                .fieldType(fieldType)
                .rawText(parsedValue)
                .parsedValue(parsedValue)
                .confidence(new BigDecimal("0.900"))
                .ocrModelVersion("mock-v1")
                .detectedAt(LocalDateTime.now())
                .build();
    }

    private DxdiagResult dxdiagResult(String cpu, String memory, String driverVersion) {
        return dxdiagResult(cpu, memory, driverVersion, LocalDateTime.now());
    }

    private DxdiagResult dxdiagResult(String cpu, String memory, String driverVersion, LocalDateTime parsedAt) {
        return DxdiagResult.builder()
                .evidenceId(DIAGNOSTIC_FILE_EVIDENCE_ID)
                .cpu(cpu)
                .memory(memory)
                .driverVersion(driverVersion)
                .parserVersion("dxdiag-v1")
                .parseStatus(ParseStatus.SUCCESS)
                .parsedAt(parsedAt)
                .build();
    }

    private DiagnosisFieldResponse fieldNamed(DiagnosisFieldListResponse response, String fieldName) {
        return response.getFields().stream()
                .filter(field -> field.getFieldName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("field not found: " + fieldName));
    }

    @Test
    void marksConflictWhenOcrAndFileParseValuesDiffer() {
        stubItemAndOwnership();
        when(evidenceRepository.findAllByListingChecklistItem_Id(ITEM_ID))
                .thenReturn(List.of(photoEvidence(), diagnosticFileEvidence()));
        when(ocrResultRepository.findAllByEvidenceIdIn(anyList()))
                .thenReturn(List.of(ocrResult(OcrFieldType.RAM, "16GB")));
        when(dxdiagResultRepository.findAllByEvidenceIdIn(anyList()))
                .thenReturn(List.of(dxdiagResult(null, "32768MB RAM", null)));
        when(batteryReportResultRepository.findAllByEvidenceIdIn(anyList())).thenReturn(List.of());

        DiagnosisFieldListResponse response = service.getDiagnosis(ITEM_ID, SELLER_ID);

        DiagnosisFieldResponse ram = fieldNamed(response, "RAM");
        assertThat(ram.getOcrValue()).isEqualTo("16GB");
        assertThat(ram.getFileParseValue()).isEqualTo("32768MB RAM");
        assertThat(ram.isConflict()).isTrue();
        assertThat(ram.getConfirmedValue()).isNull();
    }

    @Test
    void doesNotMarkConflictWhenOcrAndFileParseValuesMatch() {
        stubItemAndOwnership();
        when(evidenceRepository.findAllByListingChecklistItem_Id(ITEM_ID))
                .thenReturn(List.of(photoEvidence(), diagnosticFileEvidence()));
        when(ocrResultRepository.findAllByEvidenceIdIn(anyList()))
                .thenReturn(List.of(ocrResult(OcrFieldType.CPU, "i7-13700H")));
        when(dxdiagResultRepository.findAllByEvidenceIdIn(anyList()))
                .thenReturn(List.of(dxdiagResult("i7-13700H", null, null)));
        when(batteryReportResultRepository.findAllByEvidenceIdIn(anyList())).thenReturn(List.of());

        DiagnosisFieldListResponse response = service.getDiagnosis(ITEM_ID, SELLER_ID);

        DiagnosisFieldResponse cpu = fieldNamed(response, "CPU");
        assertThat(cpu.getOcrValue()).isEqualTo("i7-13700H");
        assertThat(cpu.getFileParseValue()).isEqualTo("i7-13700H");
        assertThat(cpu.isConflict()).isFalse();
    }

    @Test
    void usesLatestDxdiagResultWhenEvidenceWasReparsedMultipleTimes() {
        stubItemAndOwnership();
        when(evidenceRepository.findAllByListingChecklistItem_Id(ITEM_ID))
                .thenReturn(List.of(diagnosticFileEvidence()));
        LocalDateTime older = LocalDateTime.now().minusDays(1);
        LocalDateTime newer = LocalDateTime.now();
        // 리포지토리 조회 순서가 파싱 시각 순서와 다르게 와도(예: PK 역순) 최신 값이 이겨야 한다.
        when(dxdiagResultRepository.findAllByEvidenceIdIn(anyList()))
                .thenReturn(
                        List.of(
                                dxdiagResult("NEW CPU", null, null, newer),
                                dxdiagResult("OLD CPU", null, null, older)));
        when(batteryReportResultRepository.findAllByEvidenceIdIn(anyList())).thenReturn(List.of());

        DiagnosisFieldListResponse response = service.getDiagnosis(ITEM_ID, SELLER_ID);

        assertThat(fieldNamed(response, "CPU").getFileParseValue()).isEqualTo("NEW CPU");
    }

    @Test
    void usesLatestBatteryReportResultWhenEvidenceWasReparsedMultipleTimes() {
        stubItemAndOwnership();
        when(evidenceRepository.findAllByListingChecklistItem_Id(ITEM_ID))
                .thenReturn(List.of(diagnosticFileEvidence()));
        when(dxdiagResultRepository.findAllByEvidenceIdIn(anyList())).thenReturn(List.of());
        LocalDateTime older = LocalDateTime.now().minusDays(1);
        LocalDateTime newer = LocalDateTime.now();
        BatteryReportResult newResult =
                BatteryReportResult.builder()
                        .evidenceId(DIAGNOSTIC_FILE_EVIDENCE_ID)
                        .batteryManufacturer("NEW MANUFACTURER")
                        .parserVersion("battery-report-v1")
                        .parseStatus(ParseStatus.SUCCESS)
                        .parsedAt(newer)
                        .build();
        BatteryReportResult oldResult =
                BatteryReportResult.builder()
                        .evidenceId(DIAGNOSTIC_FILE_EVIDENCE_ID)
                        .batteryManufacturer("OLD MANUFACTURER")
                        .parserVersion("battery-report-v1")
                        .parseStatus(ParseStatus.SUCCESS)
                        .parsedAt(older)
                        .build();
        // 리포지토리가 최신 결과를 먼저 돌려주는 순서로 와도 파싱 시각으로 다시 정렬해야 한다.
        when(batteryReportResultRepository.findAllByEvidenceIdIn(anyList()))
                .thenReturn(List.of(newResult, oldResult));

        DiagnosisFieldListResponse response = service.getDiagnosis(ITEM_ID, SELLER_ID);

        assertThat(fieldNamed(response, "BATTERY_MANUFACTURER").getFileParseValue())
                .isEqualTo("NEW MANUFACTURER");
    }

    @Test
    void reportsOcrOnlyFieldWithoutFileParseCounterpart() {
        stubItemAndOwnership();
        when(evidenceRepository.findAllByListingChecklistItem_Id(ITEM_ID))
                .thenReturn(List.of(photoEvidence()));
        when(ocrResultRepository.findAllByEvidenceIdIn(anyList()))
                .thenReturn(List.of(ocrResult(OcrFieldType.MODEL_NAME, "Galaxy Book4 Pro")));

        DiagnosisFieldListResponse response = service.getDiagnosis(ITEM_ID, SELLER_ID);

        DiagnosisFieldResponse modelName = fieldNamed(response, "MODEL_NAME");
        assertThat(modelName.getOcrValue()).isEqualTo("Galaxy Book4 Pro");
        assertThat(modelName.getFileParseValue()).isNull();
        assertThat(modelName.isConflict()).isFalse();
    }

    @Test
    void reportsFileParseOnlyFieldWithoutOcrCounterpart() {
        stubItemAndOwnership();
        when(evidenceRepository.findAllByListingChecklistItem_Id(ITEM_ID))
                .thenReturn(List.of(diagnosticFileEvidence()));
        when(dxdiagResultRepository.findAllByEvidenceIdIn(anyList()))
                .thenReturn(List.of(dxdiagResult(null, null, "32.0.101.7084")));
        when(batteryReportResultRepository.findAllByEvidenceIdIn(anyList())).thenReturn(List.of());

        DiagnosisFieldListResponse response = service.getDiagnosis(ITEM_ID, SELLER_ID);

        DiagnosisFieldResponse driverVersion = fieldNamed(response, "DRIVER_VERSION");
        assertThat(driverVersion.getOcrValue()).isNull();
        assertThat(driverVersion.getFileParseValue()).isEqualTo("32.0.101.7084");
        assertThat(driverVersion.isConflict()).isFalse();
    }

    @Test
    void mapsDxdiagSystemFieldsIntoFileParseValues() {
        stubItemAndOwnership();
        // 기존에 생성된 체크리스트 스냅샷의 automationType이 NONE이어도 itemCode를 기준으로
        // Windows 진단 결과를 기기 정보 화면에 연결해야 한다.
        when(listingChecklistItem.getItemCode()).thenReturn("LAP-SCR-013");
        when(evidenceRepository.findAllByListingChecklistItem_Id(ITEM_ID)).thenReturn(List.of(photoEvidence()));
        when(evidenceRepository.findAllByListingId(LISTING_ID)).thenReturn(List.of(diagnosticFileEvidence()));
        DxdiagResult result =
                DxdiagResult.builder()
                        .evidenceId(DIAGNOSTIC_FILE_EVIDENCE_ID)
                        .modelName("960XFH")
                        .osVersion("Windows 11 Enterprise 64-bit")
                        .storageCapacity("975.7 GB")
                        .parserVersion("dxdiag-v1")
                        .parseStatus(ParseStatus.SUCCESS)
                        .parsedAt(LocalDateTime.now())
                        .build();
        when(dxdiagResultRepository.findAllByEvidenceIdIn(anyList())).thenReturn(List.of(result));

        DiagnosisFieldListResponse response = service.getDiagnosis(ITEM_ID, SELLER_ID);

        assertThat(fieldNamed(response, "MODEL_NAME").getFileParseValue()).isEqualTo("960XFH");
        assertThat(fieldNamed(response, "OS_VERSION").getFileParseValue())
                .isEqualTo("Windows 11 Enterprise 64-bit");
        assertThat(fieldNamed(response, "STORAGE_CAPACITY").getFileParseValue()).isEqualTo("975.7 GB");
    }

    @Test
    void separatesDeviceInfoFieldsFromWindowsSystemDiagnosis() {
        stubItemAndOwnership();
        when(listingChecklistItem.getAutomationType()).thenReturn(AutomationType.FILE_PARSE);
        when(listingChecklistItem.getParserType()).thenReturn("DXDIAG");
        when(evidenceRepository.findAllByListingChecklistItem_Id(ITEM_ID))
                .thenReturn(List.of(diagnosticFileEvidence()));
        DxdiagResult result =
                DxdiagResult.builder()
                        .evidenceId(DIAGNOSTIC_FILE_EVIDENCE_ID)
                        .modelName("960XFH")
                        .osVersion("Windows 11 Enterprise 64-bit")
                        .storageCapacity("975.7 GB")
                        .cpu("Intel Core Ultra 9 185H")
                        .memory("32768 MB RAM")
                        .gpu("Intel Arc Graphics")
                        .gpuMemory("16291 MB")
                        .driverVersion("32.0.101.7084")
                        .soundDevice("Realtek Audio")
                        .parserVersion("dxdiag-v1")
                        .parseStatus(ParseStatus.SUCCESS)
                        .parsedAt(LocalDateTime.now())
                        .build();
        when(dxdiagResultRepository.findAllByEvidenceIdIn(anyList())).thenReturn(List.of(result));
        when(batteryReportResultRepository.findAllByEvidenceIdIn(anyList())).thenReturn(List.of());

        DiagnosisFieldListResponse response = service.getDiagnosis(ITEM_ID, SELLER_ID);

        assertThat(response.getFields())
                .extracting(DiagnosisFieldResponse::getFieldName)
                .containsExactlyInAnyOrder("RAM", "GPU", "GPU_MEMORY", "DRIVER_VERSION", "SOUND_DEVICE");
    }

    @Test
    void exposesManualDeviceInfoWithoutAnyEvidence() {
        stubItemAndOwnership();
        when(listingChecklistItem.getItemCode()).thenReturn("LAP-SCR-013");
        when(listingChecklistItem.manualDiagnosisValue(any(DiagnosisFieldName.class)))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0) == DiagnosisFieldName.MODEL_NAME
                                        ? "Galaxy Book4 Ultra"
                                        : null);
        when(evidenceRepository.findAllByListingChecklistItem_Id(ITEM_ID)).thenReturn(List.of());
        when(evidenceRepository.findAllByListingId(LISTING_ID)).thenReturn(List.of());

        DiagnosisFieldListResponse response = service.getDiagnosis(ITEM_ID, SELLER_ID);

        assertThat(fieldNamed(response, "MODEL_NAME").getConfirmedValue()).isEqualTo("Galaxy Book4 Ultra");
    }

    @Test
    void mapsBatteryReportColumnsIntoFileParseValues() {
        stubItemAndOwnership();
        when(evidenceRepository.findAllByListingChecklistItem_Id(ITEM_ID))
                .thenReturn(List.of(diagnosticFileEvidence()));
        when(dxdiagResultRepository.findAllByEvidenceIdIn(anyList())).thenReturn(List.of());
        BatteryReportResult batteryReportResult =
                BatteryReportResult.builder()
                        .evidenceId(DIAGNOSTIC_FILE_EVIDENCE_ID)
                        .designCapacity("73,829 mWh")
                        .fullChargeCapacity("69,840 mWh")
                        .cycleCount(288)
                        .batteryManufacturer("SAMSUNG Electronics")
                        .capacityRatio(new BigDecimal("94.60"))
                        .parserVersion("battery-report-v1")
                        .parseStatus(ParseStatus.SUCCESS)
                        .parsedAt(LocalDateTime.now())
                        .build();
        when(batteryReportResultRepository.findAllByEvidenceIdIn(anyList())).thenReturn(List.of(batteryReportResult));

        DiagnosisFieldListResponse response = service.getDiagnosis(ITEM_ID, SELLER_ID);

        assertThat(fieldNamed(response, "DESIGN_CAPACITY").getFileParseValue()).isEqualTo("73,829 mWh");
        assertThat(fieldNamed(response, "CYCLE_COUNT").getFileParseValue()).isEqualTo("288");
        assertThat(fieldNamed(response, "CAPACITY_RATIO").getFileParseValue()).isEqualTo("94.60");
    }

    @Test
    void returnsEmptyFieldsWhenNoEvidenceExists() {
        stubItemAndOwnership();
        when(evidenceRepository.findAllByListingChecklistItem_Id(ITEM_ID)).thenReturn(List.of());

        DiagnosisFieldListResponse response = service.getDiagnosis(ITEM_ID, SELLER_ID);

        assertThat(response.getItemId()).isEqualTo(ITEM_ID);
        assertThat(response.getFields()).isEmpty();
    }

    @Test
    void fieldValueOffersOcrEvidenceAsEditTargetWhenFieldWasNeverDetected() {
        when(listingChecklistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(listingChecklistItem));
        when(listingChecklistItem.getEvidenceType()).thenReturn(EvidenceType.PHOTO);
        when(evidenceRepository.findAllByListingChecklistItem_Id(ITEM_ID))
                .thenReturn(List.of(photoEvidence()));
        when(ocrResultRepository.findAllByEvidenceIdIn(anyList())).thenReturn(List.of());

        DiagnosisAggregationService.DiagnosisFieldValue value =
                service.getFieldValue(ITEM_ID, DiagnosisFieldName.MODEL_NAME);

        assertThat(value.ocrValue()).isNull();
        assertThat(value.fileParseValue()).isNull();
        assertThat(value.sourceEvidenceId()).isEqualTo(PHOTO_EVIDENCE_ID);
        assertThat(value.sourceType()).isEqualTo(DiagnosisSourceType.OCR);
    }

    @Test
    void fieldValueOffersDxdiagEvidenceAsEditTargetEvenWhenParsingRowIsMissing() {
        when(listingChecklistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(listingChecklistItem));
        when(listingChecklistItem.getEvidenceType()).thenReturn(EvidenceType.DIAGNOSTIC_FILE);
        when(listingChecklistItem.getParserType()).thenReturn("DXDIAG");
        when(evidenceRepository.findAllByListingChecklistItem_Id(ITEM_ID))
                .thenReturn(List.of(diagnosticFileEvidence()));
        when(dxdiagResultRepository.findAllByEvidenceIdIn(anyList())).thenReturn(List.of());
        when(batteryReportResultRepository.findAllByEvidenceIdIn(anyList())).thenReturn(List.of());

        DiagnosisAggregationService.DiagnosisFieldValue value =
                service.getFieldValue(ITEM_ID, DiagnosisFieldName.SOUND_DEVICE);

        assertThat(value.sourceEvidenceId()).isEqualTo(DIAGNOSTIC_FILE_EVIDENCE_ID);
        assertThat(value.sourceType()).isEqualTo(DiagnosisSourceType.DXDIAG);
    }

    @Test
    void fieldValueOffersBatteryReportEvidenceAsEditTargetEvenWhenParsingRowIsMissing() {
        when(listingChecklistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(listingChecklistItem));
        when(listingChecklistItem.getEvidenceType()).thenReturn(EvidenceType.DIAGNOSTIC_FILE);
        when(listingChecklistItem.getParserType()).thenReturn("BATTERY_REPORT");
        when(evidenceRepository.findAllByListingChecklistItem_Id(ITEM_ID))
                .thenReturn(List.of(diagnosticFileEvidence()));
        when(dxdiagResultRepository.findAllByEvidenceIdIn(anyList())).thenReturn(List.of());
        when(batteryReportResultRepository.findAllByEvidenceIdIn(anyList())).thenReturn(List.of());

        DiagnosisAggregationService.DiagnosisFieldValue value =
                service.getFieldValue(ITEM_ID, DiagnosisFieldName.CYCLE_COUNT);

        assertThat(value.sourceEvidenceId()).isEqualTo(DIAGNOSTIC_FILE_EVIDENCE_ID);
        assertThat(value.sourceType()).isEqualTo(DiagnosisSourceType.BATTERY_REPORT);
    }

    @Test
    void fieldValueHasNoEditTargetWhenNoEvidenceUploadedAtAll() {
        when(listingChecklistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(listingChecklistItem));
        when(listingChecklistItem.getEvidenceType()).thenReturn(EvidenceType.PHOTO);
        when(evidenceRepository.findAllByListingChecklistItem_Id(ITEM_ID)).thenReturn(List.of());

        DiagnosisAggregationService.DiagnosisFieldValue value =
                service.getFieldValue(ITEM_ID, DiagnosisFieldName.MODEL_NAME);

        assertThat(value.sourceEvidenceId()).isNull();
        assertThat(value.sourceType()).isNull();
    }

    @Test
    void throwsWhenItemNotFound() {
        when(listingChecklistItemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDiagnosis(ITEM_ID, SELLER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ITEM_NOT_FOUND));
    }

    @Test
    void throwsForbiddenWhenSellerDoesNotOwnListing() {
        stubItemAndOwnership();

        assertThatThrownBy(() -> service.getDiagnosis(ITEM_ID, OTHER_MEMBER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
