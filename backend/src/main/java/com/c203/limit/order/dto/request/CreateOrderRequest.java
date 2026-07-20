package com.c203.limit.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "CreateOrderRequest", description = "유효한 구매권으로 주문을 생성하는 요청")
public class CreateOrderRequest {

    @Schema(description = "사용할 구매권 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long purchaseRightId;

    @Schema(description = "주문 상품 ID", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long productId;

    @Schema(description = "주문 수량", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int quantity;
}
