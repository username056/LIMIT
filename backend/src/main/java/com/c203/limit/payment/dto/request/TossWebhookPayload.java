package com.c203.limit.payment.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "TossWebhookPayload", description = "토스페이먼츠 Webhook 표준 페이로드")
public class TossWebhookPayload {

    @Schema(description = "토스 결제 고유키", example = "tviva20260716...", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String paymentKey;

    @Schema(description = "주문 식별자", example = "ORD-20260716-0001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String orderId;

    @Schema(description = "결제 상태", example = "DONE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "총 결제 금액", example = "123000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal totalAmount;
}
