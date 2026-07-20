package com.c203.limit.cart.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "CartResponse", description = "장바구니 전체 응답")
public class CartResponse {

    @Schema(description = "장바구니 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long cartId;

    @Schema(description = "장바구니 항목 목록", example = "[]", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<CartItemResponse> items;

    @Schema(description = "전체 상품 수량 합계", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int totalItemCount;

    @Schema(description = "현재 가격 기준 합계", example = "199000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal totalAmount;

    @Schema(description = "장바구니 최종 수정 시각", example = "2026-07-16T11:55:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime updatedAt;
}
