package com.c203.limit.domain.product.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ProductDetailResponse", description = "중고 전자기기 상품 상세")
public class ProductDetailResponse {

    @Schema(example = "1001")
    private final Long productId;

    @Schema(example = "55")
    private final Long sellerId;

    private final DeviceCategoryResponse category;

    private final DeviceInfoResponse device;

    @Schema(example = "Galaxy S24 256GB")
    private final String name;

    @Schema(example = "생활 흠집이 있습니다.")
    private final String description;

    @Schema(example = "650000")
    private final BigDecimal price;

    @Schema(example = "ON_SALE")
    private final String status;

    @Schema(example = "서울 강남구")
    private final String tradeRegion;

    private final ChecklistSummaryResponse checklistSummary;

    @Schema(example = "https://cdn.example.com/products/1001/thumbnail.jpg")
    private final String thumbnailUrl;

    @Schema(example = "2026-07-22T10:00:00+09:00")
    private final OffsetDateTime createdAt;

    @Schema(example = "2026-07-22T11:00:00+09:00")
    private final OffsetDateTime updatedAt;
}
