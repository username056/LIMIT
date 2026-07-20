package com.c203.limit.domain.statistics.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "PopularProductResponse", description = "인기 상품 랭킹 항목")
public class PopularProductResponse {

    @Schema(description = "랭킹 순위", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer rank;

    @Schema(description = "상품 ID", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long productId;

    @Schema(description = "상품명", example = "Air Jordan 1 Retro High OG", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String name;

    @Schema(description = "대표 이미지 URL", example = "https://cdn.example.com/thumb/1001.jpg", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String thumbnailUrl;

    @Schema(description = "인기 점수", example = "982.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal popularityScore;
}
