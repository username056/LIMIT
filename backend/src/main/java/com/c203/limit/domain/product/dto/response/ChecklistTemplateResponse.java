package com.c203.limit.domain.product.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ChecklistTemplateResponse", description = "모델별 활성 체크리스트 템플릿")
public class ChecklistTemplateResponse {

    @Schema(example = "501")
    private final Long templateId;

    @Schema(example = "101")
    private final Long deviceModelId;

    @Schema(example = "1")
    private final Integer version;

    private final List<ChecklistTemplateItemResponse> items;
}
