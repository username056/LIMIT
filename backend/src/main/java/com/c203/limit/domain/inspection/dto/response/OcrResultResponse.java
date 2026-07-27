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
@Schema(name = "OcrResultResponse", description = "OCR 텍스트 추출 결과")
public class OcrResultResponse {

    @Schema(example = "1")
    private final Long ocrResultId;

    @Schema(example = "9003")
    private final Long evidenceId;

    @Schema(example = "MODEL_NAME")
    private final OcrFieldType fieldType;

    @Schema(description = "OCR 원본 인식 텍스트", example = "Galaxy Book4 Pro")
    private final String rawText;

    @Schema(description = "정규화된 값", example = "Galaxy Book4 Pro")
    private final String parsedValue;

    @Schema(description = "확신도 0.000~1.000", example = "0.987")
    private final BigDecimal confidence;

    @Schema(example = "V2")
    private final String ocrModelVersion;

    @Schema(example = "2026-07-23T10:15:00")
    private final LocalDateTime detectedAt;

    public static OcrResultResponse from(OcrResult ocrResult) {
        return new OcrResultResponse(
                ocrResult.getId(),
                ocrResult.getEvidenceId(),
                ocrResult.getFieldType(),
                ocrResult.getRawText(),
                ocrResult.getParsedValue(),
                ocrResult.getConfidence(),
                ocrResult.getOcrModelVersion(),
                ocrResult.getDetectedAt());
    }
}
