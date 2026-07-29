package com.c203.limit.domain.inspection.reinspection.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ReinspectionRequestItemCreateRequest", description = "재검수 요청 항목")
public class ReinspectionRequestItemCreateRequest {

    @Schema(description = "체크리스트 항목 ID", example = "301")
    @NotNull
    private final Long checklistItemId;

    @Schema(example = "모서리 흠집이 보이도록 가까이 촬영해 주세요.")
    @NotBlank
    @Size(max = 1000)
    private final String requestContent;
}
