package com.c203.limit.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "InventorySummaryResponse", description = "상품 재고 요약")
public class InventorySummaryResponse {

    @Schema(description = "재고 ID", example = "701", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long inventoryId;

    @Schema(description = "총 물리 재고 수량", example = "50", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer totalQuantity;

    @Schema(description = "판매 가능 수량", example = "37", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer availableQuantity;

    @Schema(description = "예약중 수량", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer reservedQuantity;

    @Schema(description = "판매 확정 수량", example = "8", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer soldQuantity;

    @Schema(description = "AVAILABLE | TEMPORARILY_SOLD_OUT | SOLD_OUT | STOPPED", example = "AVAILABLE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;
}
