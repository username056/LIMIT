package com.c203.limit.statistics.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "RecommendedProductResponse", description = "개인화 추천 상품 항목")
public class RecommendedProductResponse {

    @Schema(description = "추천 순위", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer rank;

    @Schema(description = "상품 ID", example = "1044", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long productId;

    @Schema(description = "상품명", example = "New Balance 990v6", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String name;

    @Schema(description = "대표 이미지 URL", example = "https://cdn.example.com/thumb/1044.jpg", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String thumbnailUrl;

    @Schema(description = "추천 점수", example = "0.874", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal score;
}
