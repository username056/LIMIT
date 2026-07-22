package com.c203.limit.domain.stock.dto.request;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "StockReservationConfirmRequest", description = "결제 성공에 따른 재고 차감 확정 요청")
public class StockReservationConfirmRequest {

    @Schema(description = "주문 ID", example = "3001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long orderId;

    @Schema(description = "결제 ID", example = "4001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long paymentId;

    @Schema(description = "결제 완료 시각", example = "2026-07-16T10:05:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime paidAt;
}
