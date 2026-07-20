package com.c203.limit.domain.cart.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "UpdateCartItemQuantityRequest", description = "장바구니 수량 수정 요청")
public class UpdateCartItemQuantityRequest {

    @Schema(description = "변경할 수량", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int quantity;
}
