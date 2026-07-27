package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "DeviceModelSummaryResponse", description = "지원 기기 모델 목록 항목")
public class DeviceModelSummaryResponse {

    @Schema(example = "101")
    private final Long deviceModelId;

    @Schema(example = "1")
    private final Long manufacturerId;

    @Schema(example = "Samsung")
    private final String manufacturerName;

    @Schema(example = "1")
    private final Long categoryId;

    @Schema(example = "SM-S921N")
    private final String modelCode;

    @Schema(example = "Galaxy S24")
    private final String modelName;

    @Schema(example = "ANDROID")
    private final String defaultOs;

    @Schema(example = "true")
    private final boolean isActive;
}
