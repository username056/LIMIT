package com.c203.limit.domain.product.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "ProductSummaryResponse", description = "회원용 상품 목록의 항목(목록 화면 전용 축약 응답)")
public class ProductSummaryResponse {

    @Schema(description = "상품 ID", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long productId;

    @Schema(description = "상품명", example = "Air Jordan 1 Retro High OG", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String name;

    @Schema(description = "브랜드명", example = "Nike", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String brandName;

    @Schema(description = "카테고리명", example = "Sneakers", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String categoryName;

    @Schema(description = "판매 가격", example = "259000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal price;

    @Schema(description = "판매 상태", example = "ON_SALE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String saleStatus;

    @Schema(description = "대표 이미지 URL, 없으면 null", example = "https://cdn.example.com/thumb/1001.jpg", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String thumbnailUrl;

    @Schema(description = "신고 존재 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean hasReport;
}
