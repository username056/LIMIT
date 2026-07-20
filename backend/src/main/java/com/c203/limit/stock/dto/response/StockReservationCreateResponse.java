package com.c203.limit.stock.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "StockReservationCreateResponse", description = "재고 예약 결과")
public class StockReservationCreateResponse {

    @Schema(description = "재고 예약 ID", example = "901", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long reservationId;

    @Schema(description = "예약 고유 키", example = "RSV-01JABC123", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String reservationKey;

    @Schema(description = "판매 ID", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long saleId;

    @Schema(description = "예약 수량", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer quantity;

    @Schema(description = "예약 상태", example = "RESERVED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "예약 시각", example = "2026-07-16T10:04:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime reservedAt;

    @Schema(description = "예약 만료 시각", example = "2026-07-16T10:09:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime expiresAt;
}
