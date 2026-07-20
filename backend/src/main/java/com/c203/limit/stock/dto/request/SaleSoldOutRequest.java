package com.c203.limit.stock.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "SaleSoldOutRequest", description = "가용 재고 소진에 따른 품절 처리 요청")
public class SaleSoldOutRequest {

    @Schema(description = "재고 ID", example = "501", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long inventoryId;

    @Schema(description = "예상 가용 수량", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer expectedAvailableQuantity;

    @Schema(description = "품절 처리 사유", example = "INVENTORY_DEPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String reason;
}
