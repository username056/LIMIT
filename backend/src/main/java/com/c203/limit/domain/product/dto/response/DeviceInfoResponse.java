package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "DeviceInfoResponse", description = "상품의 전자기기 정보")
public class DeviceInfoResponse {

    @Schema(example = "101")
    private final Long deviceModelId;

    @Schema(example = "Samsung")
    private final String manufacturer;

    @Schema(example = "Galaxy S24")
    private final String model;

    @Schema(example = "Android")
    private final String os;

    @Schema(example = "Onyx Black")
    private final String color;

    @Schema(example = "256")
    private final Integer storageGb;
}
