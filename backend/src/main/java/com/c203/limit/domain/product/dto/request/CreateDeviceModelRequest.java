package com.c203.limit.domain.product.dto.request;

import com.c203.limit.domain.product.entity.OsFamily;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDeviceModelRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(max = 50) String manufacturer,
        @NotBlank @Size(max = 100) String modelName,
        @Size(max = 50) String modelCode,
        @NotNull OsFamily osFamily) {}
