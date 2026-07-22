package com.c203.limit.domain.stock.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "StockReservationConfirmResponse", description = "재고 차감 확정 결과")
public class StockReservationConfirmResponse {

    @Schema(description = "재고 예약 ID", example = "901", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long reservationId;

    @Schema(description = "재고 ID", example = "501", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long inventoryId;

    @Schema(description = "예약 상태", example = "CONFIRMED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "누적 판매 확정 수량", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer soldQuantity;

    @Schema(description = "잔여 가용 수량", example = "9", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer availableQuantity;

    @Schema(description = "판매 확정 시각", example = "2026-07-16T10:05:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime confirmedAt;
}
