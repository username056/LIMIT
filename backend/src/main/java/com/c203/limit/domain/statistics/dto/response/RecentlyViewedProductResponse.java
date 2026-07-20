package com.c203.limit.domain.statistics.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "RecentlyViewedProductResponse", description = "최근 본 상품 항목")
public class RecentlyViewedProductResponse {

    @Schema(description = "상품 ID", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long productId;

    @Schema(description = "상품명", example = "Air Jordan 1 Retro High OG", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String name;

    @Schema(description = "대표 이미지 URL", example = "https://cdn.example.com/thumb/1001.jpg", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String thumbnailUrl;

    @Schema(description = "조회일시", example = "2026-07-16T09:40:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant viewedAt;
}
