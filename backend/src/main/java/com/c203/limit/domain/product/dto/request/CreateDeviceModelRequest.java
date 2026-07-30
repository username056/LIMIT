package com.c203.limit.domain.product.dto.request;

import com.c203.limit.domain.product.entity.OsFamily;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(
        name = "CreateDeviceModelRequest",
        description = "상품 등록 중 목록에 없는 노트북 모델을 직접 등록하는 요청")
public record CreateDeviceModelRequest(
        @NotNull @Schema(example = "10") Long categoryId,
        @NotBlank @Size(max = 50) @Schema(example = "Samsung") String manufacturer,
        @NotBlank @Size(max = 100) @Schema(example = "Galaxy Book5 Pro") String modelName,
        @Size(max = 50) @Schema(example = "NT960XHA-KC51G") String modelCode,
        @NotNull @Schema(allowableValues = {"WINDOWS", "LINUX"}) OsFamily osFamily) {}
