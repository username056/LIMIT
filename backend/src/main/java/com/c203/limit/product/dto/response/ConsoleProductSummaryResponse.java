package com.c203.limit.product.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "ConsoleProductSummaryResponse", description = "관리자/셀러 콘솔용 상품 목록 항목")
public class ConsoleProductSummaryResponse {

    @Schema(description = "상품 ID", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long productId;

    @Schema(description = "상품명", example = "Air Jordan 1 Retro High OG", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String name;

    @Schema(description = "판매자명", example = "hypebeast_store", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String sellerName;

    @Schema(description = "브랜드명", example = "Nike", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String brandName;

    @Schema(description = "카테고리명", example = "Sneakers", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String categoryName;

    @Schema(description = "판매 상태", example = "ON_SALE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String saleStatus;

    @Schema(description = "신고 존재 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean hasReport;

    @Schema(description = "등록일시", example = "2026-07-16T10:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant createdAt;
}
