package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FavoriteProductApiResponse", description = "관심상품 단건 공통 응답")
public record FavoriteProductApiResponse(
        FavoriteProductResponse data,
        @Schema(nullable = true) Object meta) {}
