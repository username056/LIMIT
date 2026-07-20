package com.c203.limit.stock.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "StockReservationCreateRequest", description = "구매권을 사용한 재고 예약 요청")
public class StockReservationCreateRequest {

    @Schema(description = "서명 구매권 토큰", example = "signed-purchase-token", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String purchasePassToken;

    @Schema(description = "예약 수량", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer quantity;
}
