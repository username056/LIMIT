package com.c203.limit.payment.dto.event;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "PaymentFailedEventV1", description = "결제 실패 시 내부 발행되는 이벤트")
public class PaymentFailedEventV1 {

    @Schema(description = "결제 시도 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long paymentId;

    @Schema(description = "주문 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long orderId;

    @Schema(description = "실패 코드", example = "USER_CANCEL", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String failureCode;

    @Schema(description = "이벤트 발생 시각", example = "2026-07-16T12:05:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant occurredAt;
}
