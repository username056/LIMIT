package com.c203.limit.domain.inspection.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "DiagnosisFieldListResponse", description = "체크리스트 항목 하나의 진단값 취합 결과")
public class DiagnosisFieldListResponse {

    @Schema(example = "1")
    private final Long itemId;

    @Schema(description = "OCR 또는 진단 파일에서 값이 하나라도 감지된 필드 목록")
    private final List<DiagnosisFieldResponse> fields;
}
