package com.c203.limit.cart.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AddCartItemRequest", description = "장바구니 상품 추가 요청")
public class AddCartItemRequest {

    @Schema(description = "장바구니에 추가할 상품 ID", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long productId;

    @Schema(description = "추가 수량", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int quantity;
}
