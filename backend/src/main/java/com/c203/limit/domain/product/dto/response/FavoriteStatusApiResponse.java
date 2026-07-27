package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FavoriteStatusApiResponse", description = "관심상품 상태 공통 응답")
public record FavoriteStatusApiResponse(
        FavoriteStatusResponse data,
        @Schema(nullable = true) Object meta) {}
