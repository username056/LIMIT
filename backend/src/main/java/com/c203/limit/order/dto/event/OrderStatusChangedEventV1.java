package com.c203.limit.order.dto.event;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "OrderStatusChangedEventV1", description = "주문 상태 변경 시 내부 발행되는 이벤트")
public class OrderStatusChangedEventV1 {

    @Schema(description = "주문 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long orderId;

    @Schema(description = "변경 전 상태", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String previousStatus;

    @Schema(description = "변경 후 상태", example = "CANCELLED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String currentStatus;

    @Schema(description = "이벤트 발생 시각", example = "2026-07-16T12:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant occurredAt;
}
