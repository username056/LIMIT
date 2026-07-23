package com.c203.limit.domain.inspection.service;

import com.c203.limit.domain.inspection.client.NaverClovaOcrClient;
import com.c203.limit.domain.inspection.client.NaverClovaOcrResult;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.OcrResult;
import com.c203.limit.domain.inspection.enums.EvidenceProcessingStatus;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.OcrResultRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** 검수 증거 이미지를 클로바 OCR로 분석해 ocr_result에 저장하는 유스케이스. */
@Service
public class OcrExtractionService {

    private final EvidenceRepository evidenceRepository;
    private final OcrResultRepository ocrResultRepository;
    private final NaverClovaOcrClient naverClovaOcrClient;

    public OcrExtractionService(
            EvidenceRepository evidenceRepository,
            OcrResultRepository ocrResultRepository,
            NaverClovaOcrClient naverClovaOcrClient) {
        this.evidenceRepository = evidenceRepository;
        this.ocrResultRepository = ocrResultRepository;
        this.naverClovaOcrClient = naverClovaOcrClient;
    }

    public OcrResult extractText(Long evidenceId, OcrFieldType fieldType) {
        Evidence evidence =
                evidenceRepository
                        .findById(evidenceId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.EVIDENCE_NOT_FOUND));

        if (evidence.getProcessingStatus() != EvidenceProcessingStatus.READY
                || evidence.getCdnUrl() == null) {
            throw new BusinessException(ErrorCode.EVIDENCE_NOT_READY);
        }

        String format = resolveImageFormat(evidence.getMimeType());
        NaverClovaOcrResult result = naverClovaOcrClient.recognize(evidence.getCdnUrl(), format);

        OcrResult ocrResult =
                OcrResult.builder()
                        .evidenceId(evidenceId)
                        .fieldType(fieldType)
                        .rawText(result.rawText())
                        .parsedValue(result.rawText() == null ? null : result.rawText().trim())
                        .confidence(result.confidence())
                        .ocrModelVersion(result.modelVersion())
                        .detectedAt(LocalDateTime.now())
                        .build();

        return ocrResultRepository.save(ocrResult);
    }

    private String resolveImageFormat(String mimeType) {
        if (mimeType == null) {
            throw new BusinessException(ErrorCode.OCR_UNSUPPORTED_IMAGE_FORMAT);
        }
        return switch (mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/bmp" -> "bmp";
            case "image/tiff" -> "tiff";
            default -> throw new BusinessException(ErrorCode.OCR_UNSUPPORTED_IMAGE_FORMAT);
        };
    }
}
