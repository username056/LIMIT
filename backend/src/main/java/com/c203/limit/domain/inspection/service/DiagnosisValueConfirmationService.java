package com.c203.limit.domain.inspection.service;

import com.c203.limit.domain.inspection.dto.request.DiagnosisValueUpdateRequest;
import com.c203.limit.domain.inspection.dto.response.DiagnosisValueUpdateResponse;
import com.c203.limit.domain.inspection.entity.BatteryReportResult;
import com.c203.limit.domain.inspection.entity.DxdiagResult;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.entity.OcrResult;
import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import com.c203.limit.domain.inspection.enums.DiagnosisFieldName;
import com.c203.limit.domain.inspection.enums.DiagnosisSourceType;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import com.c203.limit.domain.inspection.repository.BatteryReportResultRepository;
import com.c203.limit.domain.inspection.repository.DxdiagResultRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.domain.inspection.repository.OcrResultRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자가 체크리스트 항목의 필드 값을 직접 고치는 유스케이스. 별도 확정값 테이블 없이,
 * {@link DiagnosisAggregationService#getFieldValue}가 알려주는 출처(OCR/DXDIAG/BATTERY_REPORT)의
 * 원본 결과 row를 찾아 그 컬럼을 직접 덮어쓴다. 수정 이력은 남기지 않는다.
 */
@Service
public class DiagnosisValueConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisValueConfirmationService.class);
    private static final String MANUAL_SOURCE_LABEL = "manual";
    private static final java.util.Set<String> DEVICE_INFO_ITEM_CODES = java.util.Set.of("LAP-SCR-013", "SYS-003");
    // 기기 정보 항목이 다루는 네 값. 이게 다 차면 확인된 것으로 본다.
    private static final java.util.List<DiagnosisFieldName> DEVICE_INFO_FIELDS = java.util.List.of(
            DiagnosisFieldName.MODEL_NAME,
            DiagnosisFieldName.STORAGE_CAPACITY,
            DiagnosisFieldName.OS_VERSION,
            DiagnosisFieldName.CPU);

    private final ListingChecklistItemRepository listingChecklistItemRepository;
    private final ListingOwnerReader listingOwnerReader;
    private final DiagnosisAggregationService diagnosisAggregationService;
    private final OcrResultRepository ocrResultRepository;
    private final DxdiagResultRepository dxdiagResultRepository;
    private final BatteryReportResultRepository batteryReportResultRepository;

    public DiagnosisValueConfirmationService(
            ListingChecklistItemRepository listingChecklistItemRepository,
            ListingOwnerReader listingOwnerReader,
            DiagnosisAggregationService diagnosisAggregationService,
            OcrResultRepository ocrResultRepository,
            DxdiagResultRepository dxdiagResultRepository,
            BatteryReportResultRepository batteryReportResultRepository) {
        this.listingChecklistItemRepository = listingChecklistItemRepository;
        this.listingOwnerReader = listingOwnerReader;
        this.diagnosisAggregationService = diagnosisAggregationService;
        this.ocrResultRepository = ocrResultRepository;
        this.dxdiagResultRepository = dxdiagResultRepository;
        this.batteryReportResultRepository = batteryReportResultRepository;
    }

    @Transactional
    public DiagnosisValueUpdateResponse confirm(Long itemId, Long sellerId, DiagnosisValueUpdateRequest request) {
        ListingChecklistItem item =
                listingChecklistItemRepository
                        .findById(itemId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));

        verifyOwnership(item, sellerId);

        DiagnosisFieldName fieldName = parseFieldName(request.getFieldName());

        DiagnosisAggregationService.DiagnosisFieldValue current = diagnosisAggregationService.getFieldValue(itemId, fieldName);
        if (current.sourceEvidenceId() == null || current.sourceType() == null) {
            if (item.getItemCode() == null
                    || !DEVICE_INFO_ITEM_CODES.contains(item.getItemCode())
                    || !isDeviceInfoField(fieldName)) {
                throw new BusinessException(ErrorCode.FIELD_NOT_EDITABLE);
            }
            String originalValue = item.manualDiagnosisValue(fieldName);
            item.correctManualDeviceInfo(fieldName, request.getConfirmedValue());
            markDeviceInfoCompletedIfFilled(item, itemId, fieldName, request.getConfirmedValue());
            listingChecklistItemRepository.save(item);
            return new DiagnosisValueUpdateResponse(
                    itemId, fieldName.name(), originalValue, request.getConfirmedValue(), LocalDateTime.now());
        }
        String originalValue = current.fileParseValue() != null ? current.fileParseValue() : current.ocrValue();

        applyCorrection(current.sourceType(), current.sourceEvidenceId(), fieldName, request.getConfirmedValue());
        markDeviceInfoCompletedIfFilled(item, itemId, fieldName, request.getConfirmedValue());

        log.info(
                "diagnosis value confirmed: itemId={}, fieldName={}, sourceType={}",
                itemId,
                fieldName,
                current.sourceType());

        return new DiagnosisValueUpdateResponse(
                itemId, fieldName.name(), originalValue, request.getConfirmedValue(), LocalDateTime.now());
    }

    /**
     * 기기 정보 항목은 사진 없이 값만 채워질 수 있다. 진단 프로그램이 넣어 주거나 판매자가 직접
     * 입력하는 경우인데, 그때는 증빙 row가 생기지 않아 {@code EvidenceUploadCompletionService}가
     * 손댈 일이 없고 항목이 PENDING에 남아 있었다. 판매자는 다 채웠는데 상품 목록의 검증 개수는
     * 오르지 않고 구매자에게는 미완료로 보였다.
     *
     * <p>네 값이 모두 차면 완료로 둔다. 판매하기 화면이 '자동 입력 완료'라고 부르는 기준과 같다.
     * 값의 출처는 따지지 않는다 — OCR로 두 개, 손으로 두 개 채운 경우도 다 찬 것은 마찬가지다.
     */
    private void markDeviceInfoCompletedIfFilled(
            ListingChecklistItem item, Long itemId, DiagnosisFieldName editedField, String editedValue) {
        if (item.getItemCode() == null || !DEVICE_INFO_ITEM_CODES.contains(item.getItemCode())) return;
        if (item.getCompletionStatus() == ChecklistItemCompletionStatus.COMPLETED) return;

        boolean allFilled = DEVICE_INFO_FIELDS.stream().allMatch(field -> {
            // 방금 넣은 값은 그대로 본다. 아직 flush 전이라 다시 조회하면 안 보일 수 있다.
            if (field == editedField) return hasText(editedValue);
            DiagnosisAggregationService.DiagnosisFieldValue value =
                    diagnosisAggregationService.getFieldValue(itemId, field);
            return hasText(value.fileParseValue()) || hasText(value.ocrValue());
        });
        if (!allFilled) return;

        item.markCompleted();
        log.info("device info item completed without evidence: itemId={}", itemId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isDeviceInfoField(DiagnosisFieldName fieldName) {
        return fieldName == DiagnosisFieldName.MODEL_NAME
                || fieldName == DiagnosisFieldName.STORAGE_CAPACITY
                || fieldName == DiagnosisFieldName.OS_VERSION
                || fieldName == DiagnosisFieldName.CPU;
    }

    private void applyCorrection(
            DiagnosisSourceType sourceType, Long evidenceId, DiagnosisFieldName fieldName, String newValue) {
        switch (sourceType) {
            case OCR -> correctOcrResult(evidenceId, fieldName, newValue);
            case DXDIAG -> correctDxdiagResult(evidenceId, fieldName, newValue);
            case BATTERY_REPORT -> correctBatteryReportResult(evidenceId, fieldName, newValue);
        }
    }

    /** 해당 evidence에 이 필드의 OCR 결과 row가 없으면(한 번도 인식되지 못했으면) 새로 만들어 채운다. */
    private void correctOcrResult(Long evidenceId, DiagnosisFieldName fieldName, String newValue) {
        OcrFieldType ocrFieldType = fieldName.toOcrFieldType();
        OcrResult result =
                ocrResultRepository
                        .findByEvidenceIdAndFieldType(evidenceId, ocrFieldType)
                        .orElseGet(
                                () ->
                                        OcrResult.builder()
                                                .evidenceId(evidenceId)
                                                .fieldType(ocrFieldType)
                                                .ocrModelVersion(MANUAL_SOURCE_LABEL)
                                                .detectedAt(LocalDateTime.now())
                                                .build());
        result.correctValue(newValue);
        ocrResultRepository.save(result);
    }

    /** 해당 evidence에 dxdiag 파싱 결과 row가 없으면(파싱이 전부 실패했으면) 새로 만들어 채운다. */
    private void correctDxdiagResult(Long evidenceId, DiagnosisFieldName fieldName, String newValue) {
        DxdiagResult result =
                dxdiagResultRepository
                        .findByEvidenceId(evidenceId)
                        .orElseGet(
                                () ->
                                        DxdiagResult.builder()
                                                .evidenceId(evidenceId)
                                                .parserVersion(MANUAL_SOURCE_LABEL)
                                                .parseStatus(ParseStatus.PARTIAL)
                                                .parsedAt(LocalDateTime.now())
                                                .build());
        result.correctField(fieldName, newValue);
        dxdiagResultRepository.save(result);
    }

    /** 해당 evidence에 배터리 리포트 파싱 결과 row가 없으면(파싱이 전부 실패했으면) 새로 만들어 채운다. */
    private void correctBatteryReportResult(Long evidenceId, DiagnosisFieldName fieldName, String newValue) {
        BatteryReportResult result =
                batteryReportResultRepository
                        .findByEvidenceId(evidenceId)
                        .orElseGet(
                                () ->
                                        BatteryReportResult.builder()
                                                .evidenceId(evidenceId)
                                                .parserVersion(MANUAL_SOURCE_LABEL)
                                                .parseStatus(ParseStatus.PARTIAL)
                                                .parsedAt(LocalDateTime.now())
                                                .build());
        result.correctField(fieldName, newValue);
        batteryReportResultRepository.save(result);
    }

    private DiagnosisFieldName parseFieldName(String rawFieldName) {
        try {
            return DiagnosisFieldName.valueOf(rawFieldName);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.FIELD_NOT_EDITABLE);
        }
    }

    private void verifyOwnership(ListingChecklistItem item, Long sellerId) {
        ListingOwnerReader.ListingOwnerInfo listing =
                listingOwnerReader
                        .findById(item.getListingId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        if (!listing.sellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
