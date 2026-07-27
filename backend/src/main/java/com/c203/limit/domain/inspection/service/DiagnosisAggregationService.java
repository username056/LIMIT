package com.c203.limit.domain.inspection.service;

import com.c203.limit.domain.inspection.dto.response.DiagnosisFieldListResponse;
import com.c203.limit.domain.inspection.dto.response.DiagnosisFieldResponse;
import com.c203.limit.domain.inspection.entity.BatteryReportResult;
import com.c203.limit.domain.inspection.entity.DxdiagResult;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.entity.OcrResult;
import com.c203.limit.domain.inspection.enums.DiagnosisFieldName;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.repository.BatteryReportResultRepository;
import com.c203.limit.domain.inspection.repository.DxdiagResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.domain.inspection.repository.OcrResultRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체크리스트 항목 하나에 딸린 모든 증거의 OCR 결과와 진단 파일(dxdiag/배터리 리포트) 파싱 결과를 같은
 * 필드 기준으로 취합해 상충 여부를 계산하는 조회 전용 유스케이스. 별도 테이블에 저장하지 않고 매번 다시 계산한다.
 */
@Service
public class DiagnosisAggregationService {

    private final ListingChecklistItemRepository listingChecklistItemRepository;
    private final EvidenceRepository evidenceRepository;
    private final OcrResultRepository ocrResultRepository;
    private final DxdiagResultRepository dxdiagResultRepository;
    private final BatteryReportResultRepository batteryReportResultRepository;
    private final ListingOwnerReader listingOwnerReader;

    public DiagnosisAggregationService(
            ListingChecklistItemRepository listingChecklistItemRepository,
            EvidenceRepository evidenceRepository,
            OcrResultRepository ocrResultRepository,
            DxdiagResultRepository dxdiagResultRepository,
            BatteryReportResultRepository batteryReportResultRepository,
            ListingOwnerReader listingOwnerReader) {
        this.listingChecklistItemRepository = listingChecklistItemRepository;
        this.evidenceRepository = evidenceRepository;
        this.ocrResultRepository = ocrResultRepository;
        this.dxdiagResultRepository = dxdiagResultRepository;
        this.batteryReportResultRepository = batteryReportResultRepository;
        this.listingOwnerReader = listingOwnerReader;
    }

    @Transactional(readOnly = true)
    public DiagnosisFieldListResponse getDiagnosis(Long itemId, Long sellerId) {
        ListingChecklistItem item =
                listingChecklistItemRepository
                        .findById(itemId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));

        verifyOwnership(item, sellerId);

        List<Evidence> evidences = evidenceRepository.findAllByListingChecklistItem_Id(itemId);
        List<Long> photoEvidenceIds =
                evidences.stream()
                        .filter(evidence -> evidence.getEvidenceType() == EvidenceType.PHOTO)
                        .map(Evidence::getId)
                        .toList();
        List<Long> diagnosticFileEvidenceIds =
                evidences.stream()
                        .filter(evidence -> evidence.getEvidenceType() == EvidenceType.DIAGNOSTIC_FILE)
                        .map(Evidence::getId)
                        .toList();

        Map<DiagnosisFieldName, String> ocrValues = collectOcrValues(photoEvidenceIds);
        Map<DiagnosisFieldName, String> fileParseValues = collectFileParseValues(diagnosticFileEvidenceIds);

        List<DiagnosisFieldResponse> fields = buildFields(ocrValues, fileParseValues);

        return new DiagnosisFieldListResponse(itemId, fields);
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

    private Map<DiagnosisFieldName, String> collectOcrValues(List<Long> photoEvidenceIds) {
        Map<DiagnosisFieldName, String> values = new EnumMap<>(DiagnosisFieldName.class);
        if (photoEvidenceIds.isEmpty()) {
            return values;
        }
        ocrResultRepository.findAllByEvidenceIdIn(photoEvidenceIds).stream()
                .sorted(Comparator.comparing(OcrResult::getDetectedAt))
                .forEach(
                        result -> {
                            DiagnosisFieldName fieldName = DiagnosisFieldName.fromOcrFieldType(result.getFieldType());
                            putIfPresent(values, fieldName, result.getParsedValue());
                        });
        return values;
    }

    private Map<DiagnosisFieldName, String> collectFileParseValues(List<Long> diagnosticFileEvidenceIds) {
        Map<DiagnosisFieldName, String> values = new EnumMap<>(DiagnosisFieldName.class);
        if (diagnosticFileEvidenceIds.isEmpty()) {
            return values;
        }

        dxdiagResultRepository.findAllByEvidenceIdIn(diagnosticFileEvidenceIds).stream()
                .sorted(Comparator.comparing(DxdiagResult::getParsedAt))
                .forEach(
                        result -> {
                            putIfPresent(values, DiagnosisFieldName.CPU, result.getCpu());
                            putIfPresent(values, DiagnosisFieldName.RAM, result.getMemory());
                            putIfPresent(values, DiagnosisFieldName.GPU, result.getGpu());
                            putIfPresent(values, DiagnosisFieldName.GPU_MEMORY, result.getGpuMemory());
                            putIfPresent(values, DiagnosisFieldName.DRIVER_VERSION, result.getDriverVersion());
                            putIfPresent(values, DiagnosisFieldName.SOUND_DEVICE, result.getSoundDevice());
                        });

        batteryReportResultRepository.findAllByEvidenceIdIn(diagnosticFileEvidenceIds).stream()
                .sorted(Comparator.comparing(BatteryReportResult::getParsedAt))
                .forEach(
                        result -> {
                            putIfPresent(values, DiagnosisFieldName.DESIGN_CAPACITY, result.getDesignCapacity());
                            putIfPresent(
                                    values, DiagnosisFieldName.FULL_CHARGE_CAPACITY, result.getFullChargeCapacity());
                            putIfPresent(
                                    values,
                                    DiagnosisFieldName.CYCLE_COUNT,
                                    result.getCycleCount() == null ? null : String.valueOf(result.getCycleCount()));
                            putIfPresent(
                                    values, DiagnosisFieldName.BATTERY_MANUFACTURER, result.getBatteryManufacturer());
                            putIfPresent(
                                    values,
                                    DiagnosisFieldName.CAPACITY_RATIO,
                                    result.getCapacityRatio() == null
                                            ? null
                                            : result.getCapacityRatio().toPlainString());
                        });
        return values;
    }

    private void putIfPresent(Map<DiagnosisFieldName, String> values, DiagnosisFieldName fieldName, String value) {
        if (fieldName != null && value != null) {
            values.put(fieldName, value);
        }
    }

    private List<DiagnosisFieldResponse> buildFields(
            Map<DiagnosisFieldName, String> ocrValues, Map<DiagnosisFieldName, String> fileParseValues) {
        return Arrays.stream(DiagnosisFieldName.values())
                .map(fieldName -> toFieldResponse(fieldName, ocrValues.get(fieldName), fileParseValues.get(fieldName)))
                .filter(Objects::nonNull)
                .toList();
    }

    private DiagnosisFieldResponse toFieldResponse(
            DiagnosisFieldName fieldName, String ocrValue, String fileParseValue) {
        if (ocrValue == null && fileParseValue == null) {
            return null;
        }
        boolean conflict = ocrValue != null && fileParseValue != null && !ocrValue.equals(fileParseValue);
        return new DiagnosisFieldResponse(fieldName.name(), ocrValue, fileParseValue, conflict, null);
    }
}
