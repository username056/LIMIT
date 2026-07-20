package com.c203.limit.refund.dto.event;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "RefundCompletedEventV1", description = "환불 완료 시 내부 발행되는 이벤트")
public class RefundCompletedEventV1 {

    @Schema(description = "환불 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long refundId;

    @Schema(description = "결제 시도 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long paymentId;

    @Schema(description = "주문 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long orderId;

    @Schema(description = "환불 금액", example = "123000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal cancelAmount;

    @Schema(description = "이벤트 발생 시각", example = "2026-07-16T12:35:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant occurredAt;
}
