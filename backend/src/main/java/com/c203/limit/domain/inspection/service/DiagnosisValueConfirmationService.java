package com.c203.limit.domain.inspection.service;

import com.c203.limit.domain.inspection.dto.request.DiagnosisValueUpdateRequest;
import com.c203.limit.domain.inspection.dto.response.DiagnosisValueUpdateResponse;
import com.c203.limit.domain.inspection.entity.BatteryReportResult;
import com.c203.limit.domain.inspection.entity.DxdiagResult;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.entity.OcrResult;
import com.c203.limit.domain.inspection.enums.DiagnosisFieldName;
import com.c203.limit.domain.inspection.enums.DiagnosisSourceType;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
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
            throw new BusinessException(ErrorCode.FIELD_NOT_EDITABLE);
        }
        String originalValue = current.fileParseValue() != null ? current.fileParseValue() : current.ocrValue();

        applyCorrection(current.sourceType(), current.sourceEvidenceId(), fieldName, request.getConfirmedValue());

        log.info(
                "diagnosis value confirmed: itemId={}, fieldName={}, sourceType={}",
                itemId,
                fieldName,
                current.sourceType());

        return new DiagnosisValueUpdateResponse(
                itemId, fieldName.name(), originalValue, request.getConfirmedValue(), LocalDateTime.now());
    }

    private void applyCorrection(
            DiagnosisSourceType sourceType, Long evidenceId, DiagnosisFieldName fieldName, String newValue) {
        switch (sourceType) {
            case OCR -> correctOcrResult(evidenceId, fieldName, newValue);
            case DXDIAG -> correctDxdiagResult(evidenceId, fieldName, newValue);
            case BATTERY_REPORT -> correctBatteryReportResult(evidenceId, fieldName, newValue);
        }
    }

    private void correctOcrResult(Long evidenceId, DiagnosisFieldName fieldName, String newValue) {
        OcrFieldType ocrFieldType = fieldName.toOcrFieldType();
        OcrResult result =
                ocrResultRepository
                        .findByEvidenceIdAndFieldType(evidenceId, ocrFieldType)
                        .orElseThrow(() -> new BusinessException(ErrorCode.FIELD_NOT_EDITABLE));
        result.correctValue(newValue);
        ocrResultRepository.save(result);
    }

    private void correctDxdiagResult(Long evidenceId, DiagnosisFieldName fieldName, String newValue) {
        DxdiagResult result =
                dxdiagResultRepository
                        .findByEvidenceId(evidenceId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.FIELD_NOT_EDITABLE));
        result.correctField(fieldName, newValue);
        dxdiagResultRepository.save(result);
    }

    private void correctBatteryReportResult(Long evidenceId, DiagnosisFieldName fieldName, String newValue) {
        BatteryReportResult result =
                batteryReportResultRepository
                        .findByEvidenceId(evidenceId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.FIELD_NOT_EDITABLE));
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
