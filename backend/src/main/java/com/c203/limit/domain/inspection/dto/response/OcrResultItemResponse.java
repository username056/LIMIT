package com.c203.limit.domain.inspection.dto.response;

import com.c203.limit.domain.inspection.entity.OcrResult;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "OcrResultItemResponse", description = "OCR로 감지된 필드 하나의 구조화 결과")
public class OcrResultItemResponse {

    @Schema(example = "1")
    private final Long ocrResultId;

    @Schema(example = "MODEL_NAME")
    private final OcrFieldType fieldType;

    @Schema(description = "정규화된 값", example = "512GB")
    private final String parsedValue;

    @Schema(description = "OCR 원본 인식 텍스트", example = "512 GB")
    private final String rawText;

    @Schema(description = "확신도 0.000~1.000", example = "0.900")
    private final BigDecimal confidence;

    @Schema(example = "mock-v1")
    private final String ocrModelVersion;

    @Schema(example = "2026-07-27T10:15:00")
    private final LocalDateTime detectedAt;

    public static OcrResultItemResponse from(OcrResult ocrResult) {
        return new OcrResultItemResponse(
                ocrResult.getId(),
                ocrResult.getFieldType(),
                ocrResult.getParsedValue(),
                ocrResult.getRawText(),
                ocrResult.getConfidence(),
                ocrResult.getOcrModelVersion(),
                ocrResult.getDetectedAt());
    }
}
