package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "DeviceCategoryResponse", description = "전자기기 카테고리")
public class DeviceCategoryResponse {

    @Schema(example = "1")
    private final Long categoryId;

    @Schema(example = "SMARTPHONE_BAR")
    private final String code;

    @Schema(example = "일반형 스마트폰")
    private final String name;

    @Schema(example = "null")
    private final Long parentId;

    @Schema(example = "true")
    private final boolean isActive;
}
