package com.c203.limit.domain.inspection.service;

import com.c203.limit.domain.inspection.dto.response.DiagnosisFieldListResponse;
import com.c203.limit.domain.inspection.dto.response.DiagnosisFieldResponse;
import com.c203.limit.domain.inspection.entity.BatteryReportResult;
import com.c203.limit.domain.inspection.entity.DxdiagResult;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.entity.OcrResult;
import com.c203.limit.domain.inspection.enums.DiagnosisFieldName;
import com.c203.limit.domain.inspection.enums.DiagnosisSourceType;
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

        Map<DiagnosisFieldName, FieldSource> ocrValues = collectOcrValues(itemId);
        Map<DiagnosisFieldName, FieldSource> fileParseValues = collectFileParseValues(itemId);

        List<DiagnosisFieldResponse> fields = buildFields(ocrValues, fileParseValues);

        return new DiagnosisFieldListResponse(itemId, fields);
    }

    /**
     * 필드 하나에 대해 OCR/진단파일 원본값과, 우선순위(파일파싱 &gt; OCR)로 골랐을 때의 출처(증거 id·소스 타입)를
     * 반환한다. 소유권 검증은 호출자가 이미 끝냈다고 가정한다(내부 유스케이스 간 호출용).
     */
    @Transactional(readOnly = true)
    public DiagnosisFieldValue getFieldValue(Long itemId, DiagnosisFieldName fieldName) {
        FieldSource ocr = collectOcrValues(itemId).get(fieldName);
        FieldSource fileParse = collectFileParseValues(itemId).get(fieldName);
        FieldSource primary = fileParse != null ? fileParse : ocr;

        return new DiagnosisFieldValue(
                ocr == null ? null : ocr.value(),
                fileParse == null ? null : fileParse.value(),
                primary == null ? null : primary.evidenceId(),
                primary == null ? null : primary.sourceType());
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

    private Map<DiagnosisFieldName, FieldSource> collectOcrValues(Long itemId) {
        List<Long> photoEvidenceIds = evidenceIdsOfType(itemId, EvidenceType.PHOTO);

        Map<DiagnosisFieldName, FieldSource> values = new EnumMap<>(DiagnosisFieldName.class);
        if (photoEvidenceIds.isEmpty()) {
            return values;
        }
        ocrResultRepository.findAllByEvidenceIdIn(photoEvidenceIds).stream()
                .sorted(Comparator.comparing(OcrResult::getDetectedAt))
                .forEach(
                        result -> {
                            DiagnosisFieldName fieldName = DiagnosisFieldName.fromOcrFieldType(result.getFieldType());
                            putIfPresent(
                                    values,
                                    fieldName,
                                    result.getParsedValue(),
                                    result.getEvidenceId(),
                                    DiagnosisSourceType.OCR);
                        });
        return values;
    }

    private Map<DiagnosisFieldName, FieldSource> collectFileParseValues(Long itemId) {
        List<Long> diagnosticFileEvidenceIds = evidenceIdsOfType(itemId, EvidenceType.DIAGNOSTIC_FILE);

        Map<DiagnosisFieldName, FieldSource> values = new EnumMap<>(DiagnosisFieldName.class);
        if (diagnosticFileEvidenceIds.isEmpty()) {
            return values;
        }

        dxdiagResultRepository.findAllByEvidenceIdIn(diagnosticFileEvidenceIds).stream()
                .sorted(Comparator.comparing(DxdiagResult::getParsedAt))
                .forEach(
                        result -> {
                            Long evidenceId = result.getEvidenceId();
                            putIfPresent(values, DiagnosisFieldName.CPU, result.getCpu(), evidenceId, DiagnosisSourceType.DXDIAG);
                            putIfPresent(
                                    values, DiagnosisFieldName.RAM, result.getMemory(), evidenceId, DiagnosisSourceType.DXDIAG);
                            putIfPresent(values, DiagnosisFieldName.GPU, result.getGpu(), evidenceId, DiagnosisSourceType.DXDIAG);
                            putIfPresent(
                                    values,
                                    DiagnosisFieldName.GPU_MEMORY,
                                    result.getGpuMemory(),
                                    evidenceId,
                                    DiagnosisSourceType.DXDIAG);
                            putIfPresent(
                                    values,
                                    DiagnosisFieldName.DRIVER_VERSION,
                                    result.getDriverVersion(),
                                    evidenceId,
                                    DiagnosisSourceType.DXDIAG);
                            putIfPresent(
                                    values,
                                    DiagnosisFieldName.SOUND_DEVICE,
                                    result.getSoundDevice(),
                                    evidenceId,
                                    DiagnosisSourceType.DXDIAG);
                        });

        batteryReportResultRepository.findAllByEvidenceIdIn(diagnosticFileEvidenceIds).stream()
                .sorted(Comparator.comparing(BatteryReportResult::getParsedAt))
                .forEach(
                        result -> {
                            Long evidenceId = result.getEvidenceId();
                            putIfPresent(
                                    values,
                                    DiagnosisFieldName.DESIGN_CAPACITY,
                                    result.getDesignCapacity(),
                                    evidenceId,
                                    DiagnosisSourceType.BATTERY_REPORT);
                            putIfPresent(
                                    values,
                                    DiagnosisFieldName.FULL_CHARGE_CAPACITY,
                                    result.getFullChargeCapacity(),
                                    evidenceId,
                                    DiagnosisSourceType.BATTERY_REPORT);
                            putIfPresent(
                                    values,
                                    DiagnosisFieldName.CYCLE_COUNT,
                                    result.getCycleCount() == null ? null : String.valueOf(result.getCycleCount()),
                                    evidenceId,
                                    DiagnosisSourceType.BATTERY_REPORT);
                            putIfPresent(
                                    values,
                                    DiagnosisFieldName.BATTERY_MANUFACTURER,
                                    result.getBatteryManufacturer(),
                                    evidenceId,
                                    DiagnosisSourceType.BATTERY_REPORT);
                            putIfPresent(
                                    values,
                                    DiagnosisFieldName.CAPACITY_RATIO,
                                    result.getCapacityRatio() == null ? null : result.getCapacityRatio().toPlainString(),
                                    evidenceId,
                                    DiagnosisSourceType.BATTERY_REPORT);
                        });
        return values;
    }

    private List<Long> evidenceIdsOfType(Long itemId, EvidenceType evidenceType) {
        return evidenceRepository.findAllByListingChecklistItem_Id(itemId).stream()
                .filter(evidence -> evidence.getEvidenceType() == evidenceType)
                .map(Evidence::getId)
                .toList();
    }

    private void putIfPresent(
            Map<DiagnosisFieldName, FieldSource> values,
            DiagnosisFieldName fieldName,
            String value,
            Long evidenceId,
            DiagnosisSourceType sourceType) {
        if (fieldName != null && value != null) {
            values.put(fieldName, new FieldSource(value, evidenceId, sourceType));
        }
    }

    private List<DiagnosisFieldResponse> buildFields(
            Map<DiagnosisFieldName, FieldSource> ocrValues, Map<DiagnosisFieldName, FieldSource> fileParseValues) {
        return Arrays.stream(DiagnosisFieldName.values())
                .map(fieldName -> toFieldResponse(fieldName, ocrValues.get(fieldName), fileParseValues.get(fieldName)))
                .filter(Objects::nonNull)
                .toList();
    }

    private DiagnosisFieldResponse toFieldResponse(
            DiagnosisFieldName fieldName, FieldSource ocr, FieldSource fileParse) {
        if (ocr == null && fileParse == null) {
            return null;
        }
        String ocrValue = ocr == null ? null : ocr.value();
        String fileParseValue = fileParse == null ? null : fileParse.value();
        boolean conflict = ocrValue != null && fileParseValue != null && !ocrValue.equals(fileParseValue);
        return new DiagnosisFieldResponse(fieldName.name(), ocrValue, fileParseValue, conflict, null);
    }

    /** 필드 하나의 값과, 그 값이 어느 evidence·어느 소스(OCR/DXDIAG/BATTERY_REPORT)에서 왔는지. */
    private record FieldSource(String value, Long evidenceId, DiagnosisSourceType sourceType) {}

    /** {@link #getFieldValue} 응답: OCR/파일파싱 원본값과, 우선순위로 고른 값의 출처. */
    public record DiagnosisFieldValue(
            String ocrValue, String fileParseValue, Long sourceEvidenceId, DiagnosisSourceType sourceType) {}
}
