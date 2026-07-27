package com.c203.limit.domain.inspection.service;

import com.c203.limit.domain.inspection.client.OcrClient;
import com.c203.limit.domain.inspection.dto.OcrFieldExtraction;
import com.c203.limit.domain.inspection.dto.response.OcrResultItemResponse;
import com.c203.limit.domain.inspection.dto.response.OcrResultResponse;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.OcrResult;
import com.c203.limit.domain.inspection.enums.EvidenceProcessingStatus;
import com.c203.limit.domain.inspection.enums.OcrExtractionStatus;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.domain.inspection.repository.OcrResultRepository;
import com.c203.limit.domain.inspection.util.UnitNormalizer;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 검수 증거 스크린샷을 OCR로 분석해 기대 필드들을 한 번에 구조화하는 유스케이스. */
@Service
public class OcrExtractionService {

    private final EvidenceRepository evidenceRepository;
    private final OcrResultRepository ocrResultRepository;
    private final ListingOwnerReader listingOwnerReader;
    private final OcrClient ocrClient;

    public OcrExtractionService(
            EvidenceRepository evidenceRepository,
            OcrResultRepository ocrResultRepository,
            ListingOwnerReader listingOwnerReader,
            OcrClient ocrClient) {
        this.evidenceRepository = evidenceRepository;
        this.ocrResultRepository = ocrResultRepository;
        this.listingOwnerReader = listingOwnerReader;
        this.ocrClient = ocrClient;
    }

    public OcrResultResponse extractAndStructure(Long evidenceId, Long sellerId) {
        Evidence evidence =
                evidenceRepository
                        .findById(evidenceId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.EVIDENCE_NOT_FOUND));

        verifyOwnership(evidence, sellerId);

        if (evidence.getProcessingStatus() != EvidenceProcessingStatus.READY
                || evidence.getCdnUrl() == null) {
            throw new BusinessException(ErrorCode.EVIDENCE_NOT_READY);
        }

        Set<OcrFieldType> expectedFieldTypes = OcrFieldExpectations.SCREENSHOT_FIELD_TYPES;
        List<OcrFieldExtraction> extractions =
                detectFields(evidence.getCdnUrl(), evidence.getMimeType(), expectedFieldTypes);

        LocalDateTime detectedAt = LocalDateTime.now();
        String modelVersion = ocrClient.getModelVersion();
        List<OcrResult> savedResults =
                extractions.stream()
                        .map(extraction -> toOcrResult(evidenceId, extraction, modelVersion, detectedAt))
                        .map(ocrResultRepository::save)
                        .toList();

        return buildResponse(evidenceId, expectedFieldTypes, savedResults);
    }

    private void verifyOwnership(Evidence evidence, Long sellerId) {
        ListingOwnerReader.ListingOwnerInfo listing =
                listingOwnerReader
                        .findById(evidence.getListingId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.EVIDENCE_NOT_FOUND));
        if (!listing.sellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private List<OcrFieldExtraction> detectFields(
            String imageUrl, String mimeType, Set<OcrFieldType> expectedFieldTypes) {
        try {
            return ocrClient.extractFields(imageUrl, mimeType, expectedFieldTypes);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.PARSING_FAILED);
        }
    }

    private OcrResult toOcrResult(
            Long evidenceId, OcrFieldExtraction extraction, String modelVersion, LocalDateTime detectedAt) {
        return OcrResult.builder()
                .evidenceId(evidenceId)
                .fieldType(extraction.fieldType())
                .rawText(extraction.rawText())
                .parsedValue(UnitNormalizer.normalize(extraction.fieldType(), extraction.parsedValue()))
                .confidence(extraction.confidence())
                .ocrModelVersion(modelVersion)
                .detectedAt(detectedAt)
                .build();
    }

    private OcrResultResponse buildResponse(
            Long evidenceId, Set<OcrFieldType> expectedFieldTypes, List<OcrResult> savedResults) {
        Set<OcrFieldType> detectedFieldTypes = EnumSet.noneOf(OcrFieldType.class);
        savedResults.forEach(result -> detectedFieldTypes.add(result.getFieldType()));

        Set<OcrFieldType> missingFieldTypes = EnumSet.copyOf(expectedFieldTypes);
        missingFieldTypes.removeAll(detectedFieldTypes);

        OcrExtractionStatus status;
        if (detectedFieldTypes.isEmpty()) {
            status = OcrExtractionStatus.FAILED;
        } else if (missingFieldTypes.isEmpty()) {
            status = OcrExtractionStatus.SUCCESS;
        } else {
            status = OcrExtractionStatus.PARTIAL;
        }

        List<OcrResultItemResponse> results = savedResults.stream().map(OcrResultItemResponse::from).toList();
        return OcrResultResponse.of(evidenceId, results, status, missingFieldTypes);
    }
}
