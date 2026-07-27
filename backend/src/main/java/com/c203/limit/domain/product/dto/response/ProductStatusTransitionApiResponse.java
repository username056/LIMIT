package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProductStatusTransitionApiResponse", description = "상품 상태 전환 공통 응답")
public record ProductStatusTransitionApiResponse(
        ProductStatusTransitionResponse data,
        @Schema(nullable = true) Object meta) {}
