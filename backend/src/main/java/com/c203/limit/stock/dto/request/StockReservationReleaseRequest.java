package com.c203.limit.stock.dto.request;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "StockReservationReleaseRequest", description = "결제 실패·주문 취소에 따른 재고 복구 요청")
public class StockReservationReleaseRequest {

    @Schema(description = "재고 복구 사유", example = "PAYMENT_FAILED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String reason;

    @Schema(description = "관련 주문 ID", example = "3001", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Long orderId;

    @Schema(description = "복구 사유 발생 시각", example = "2026-07-16T10:06:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime occurredAt;
}
