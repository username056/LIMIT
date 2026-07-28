package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProductDetailApiResponse", description = "상품 상세 공통 응답")
public record ProductDetailApiResponse(
        ProductDetailResponse data,
        @Schema(nullable = true) Object meta) {}
