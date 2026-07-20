package com.c203.limit.product.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "ProductDetailResponse", description = "상품 상세 응답")
public class ProductDetailResponse {

    @Schema(description = "상품 ID", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long productId;

    @Schema(description = "판매자 ID", example = "55", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long sellerId;

    @Schema(description = "판매자명", example = "hypebeast_store", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String sellerName;

    @Schema(description = "브랜드 ID", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long brandId;

    @Schema(description = "브랜드명", example = "Nike", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String brandName;

    @Schema(description = "카테고리 ID", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long categoryId;

    @Schema(description = "카테고리명", example = "Sneakers", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String categoryName;

    @Schema(description = "상품명", example = "Air Jordan 1 Retro High OG", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String name;

    @Schema(description = "상품 설명", example = "정품 미개봉 제품입니다.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String description;

    @Schema(description = "판매 가격", example = "259000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal price;

    @Schema(description = "BEFORE_SALE | ON_SALE | SOLD_OUT | HIDDEN | SUSPENDED", example = "ON_SALE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String saleStatus;

    @Schema(description = "신고 존재 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean hasReport;

    @Schema(description = "판매 시작 시각, 판매 전이면 null", example = "2026-07-16T09:00:00+09:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Instant saleStartedAt;

    @Schema(description = "상품 이미지 목록", example = "[]", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<ProductImageResponse> images;

    @Schema(description = "정품 증빙 목록", example = "[]", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<ProductAuthenticityProofResponse> authenticityProofs;

    @Schema(description = "재고 요약", example = "-", requiredMode = Schema.RequiredMode.REQUIRED)
    private final InventorySummaryResponse inventory;

    @Schema(description = "등록일시", example = "2026-07-16T10:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant createdAt;

    @Schema(description = "수정일시", example = "2026-07-16T11:20:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant updatedAt;
}
