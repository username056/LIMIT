package com.c203.limit.domain.inspection.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "DiagnosisValueUpdateRequest", description = "진단값 확정/수정 요청")
public class DiagnosisValueUpdateRequest {

    @Schema(description = "확정할 필드명 (DiagnosisFieldName)", example = "CPU")
    @NotBlank
    private final String fieldName;

    @Schema(example = "13th Gen Intel(R) Core(TM) i7-13700H")
    @NotBlank
    private final String confirmedValue;
}
