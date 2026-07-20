package com.c203.limit.domain.favorite.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "FavoriteProductResponse", description = "관심 상품 응답")
public class FavoriteProductResponse {

    @Schema(description = "관심 상품 등록 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long favoriteProductId;

    @Schema(description = "상품 ID", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long productId;

    @Schema(description = "상품명", example = "Limited Sneaker", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String productName;

    @Schema(description = "대표 이미지 URL", example = "https://...", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String thumbnailUrl;

    @Schema(description = "판매 상태", example = "UPCOMING", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String saleStatus;

    @Schema(description = "등록 시각", example = "2026-07-10T10:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime createdAt;
}
