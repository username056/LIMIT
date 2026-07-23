package com.c203.limit.domain.product.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ProductSummaryResponse", description = "공개 상품 목록 항목")
public class ProductSummaryResponse {

    @Schema(example = "1001")
    private final Long productId;

    @Schema(example = "Galaxy S24 256GB")
    private final String name;

    @Schema(example = "Samsung")
    private final String manufacturerName;

    @Schema(example = "Galaxy S24")
    private final String modelName;

    @Schema(example = "650000")
    private final BigDecimal price;

    @Schema(example = "ON_SALE")
    private final String status;

    @Schema(example = "COMPLETED")
    private final String verificationStatus;

    @Schema(example = "https://cdn.example.com/products/1001/thumbnail.jpg")
    private final String thumbnailUrl;

    @Schema(example = "서울 강남구")
    private final String tradeRegion;
}
