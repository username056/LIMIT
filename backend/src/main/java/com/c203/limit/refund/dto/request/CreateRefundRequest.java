package com.c203.limit.refund.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "CreateRefundRequest", description = "환불 요청")
public class CreateRefundRequest {

    @Schema(description = "환불(취소) 금액", example = "123000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal cancelAmount;

    @Schema(description = "환불 사유", example = "단순 변심", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String reason;

    @Schema(description = "환불 트리거 유형(ORDER_CANCEL | INSPECTION_FAILED | DISPUTE_RESOLVED)", example = "ORDER_CANCEL", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String triggerType;
}
