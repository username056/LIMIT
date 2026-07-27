package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProductCreatedApiResponse", description = "상품 생성 공통 응답")
public record ProductCreatedApiResponse(
        ProductCreatedResponse data,
        @Schema(nullable = true) Object meta) {}
