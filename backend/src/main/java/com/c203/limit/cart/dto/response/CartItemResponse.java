package com.c203.limit.cart.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "CartItemResponse", description = "장바구니 항목")
public class CartItemResponse {

    @Schema(description = "장바구니 항목 ID", example = "11", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long cartItemId;

    @Schema(description = "상품 ID", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long productId;

    @Schema(description = "상품명", example = "Limited Sneaker", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String productName;

    @Schema(description = "수량", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int quantity;

    @Schema(description = "단가", example = "199000", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final BigDecimal unitPrice;

    @Schema(description = "판매 상태", example = "ON_SALE", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String saleStatus;

    @Schema(description = "재고 구매 가능 여부", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final boolean isStockAvailable;

    @Schema(description = "추가 시각", example = "2026-07-16T12:00:00+09:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime createdAt;

    @Schema(description = "수정 시각", example = "2026-07-16T12:05:00+09:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime updatedAt;
}
