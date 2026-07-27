package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ChecklistSummaryResponse", description = "상품 검증 체크리스트 진행 요약")
public class ChecklistSummaryResponse {

    @Schema(example = "18")
    private final Integer required;

    @Schema(example = "18")
    private final Integer completed;

    @Schema(example = "0")
    private final Integer recaptureRequested;
}
