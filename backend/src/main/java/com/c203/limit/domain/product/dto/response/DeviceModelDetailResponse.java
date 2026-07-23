package com.c203.limit.domain.product.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "DeviceModelDetailResponse", description = "지원 기기 모델 상세")
public class DeviceModelDetailResponse {

    @Schema(example = "101")
    private final Long deviceModelId;

    @Schema(example = "Samsung")
    private final String manufacturerName;

    @Schema(example = "일반형 스마트폰")
    private final String categoryName;

    @Schema(example = "SM-S921N")
    private final String modelCode;

    @Schema(example = "Galaxy S24")
    private final String modelName;

    @Schema(example = "ANDROID")
    private final String defaultOs;

    @Schema(example = "[128, 256, 512]")
    private final List<Integer> supportedStorageGb;

    @Schema(example = "1")
    private final Integer checklistTemplateVersion;

    @Schema(example = "true")
    private final boolean isHandoverGuideAvailable;
}
