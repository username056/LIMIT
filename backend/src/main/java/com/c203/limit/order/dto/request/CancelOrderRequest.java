package com.c203.limit.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "CancelOrderRequest", description = "주문 취소 요청")
public class CancelOrderRequest {

    @Schema(description = "취소 사유 유형(BUYER_SIMPLE_CHANGE | SELLER_FAULT | NOT_DELIVERED)", example = "BUYER_SIMPLE_CHANGE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String reasonType;

    @Schema(description = "취소 상세 사유", example = "단순 변심", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String detail;
}
