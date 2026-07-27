package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ChecklistTemplateItemResponse", description = "모델 체크리스트 템플릿 항목")
public class ChecklistTemplateItemResponse {

    @Schema(example = "SP-DSP-002")
    private final String itemCode;

    @Schema(example = "화면 전체 터치")
    private final String name;

    @Schema(example = "VIDEO")
    private final String evidenceType;

    @Schema(example = "true")
    private final boolean isRequired;

    @Schema(example = "null")
    private final Integer minCount;

    @Schema(example = "null")
    private final Integer maxCount;

    @Schema(example = "15")
    private final Integer minDurationSeconds;

    @Schema(example = "60")
    private final Integer maxDurationSeconds;

    @Schema(example = "화면 전체 격자를 끊김 없이 드래그하세요.")
    private final String guide;

    @Schema(example = "true")
    private final boolean isRecaptureAllowed;

    @Schema(example = "true")
    private final boolean isVisibleToBuyer;

    @Schema(example = "false")
    private final boolean isPrivacyMaskingRequired;
}
