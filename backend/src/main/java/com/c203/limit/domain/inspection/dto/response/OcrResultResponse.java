package com.c203.limit.domain.inspection.dto.response;

import com.c203.limit.domain.inspection.enums.OcrExtractionStatus;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "OcrResultResponse", description = "증거 1건에 대한 OCR 자동 구조화 결과")
public class OcrResultResponse {

    @Schema(example = "9003")
    private final Long evidenceId;

    private final List<OcrResultItemResponse> results;

    @Schema(example = "PARTIAL")
    private final OcrExtractionStatus status;

    @Schema(description = "기대 필드 중 감지되지 않은 필드 타입", example = "[\"GPU\"]")
    private final List<String> missingFieldTypes;

    public static OcrResultResponse of(
            Long evidenceId,
            List<OcrResultItemResponse> results,
            OcrExtractionStatus status,
            Set<OcrFieldType> missingFieldTypes) {
        List<String> missingFieldNames =
                missingFieldTypes.stream().map(Enum::name).sorted().toList();
        return new OcrResultResponse(evidenceId, results, status, missingFieldNames);
    }
}
