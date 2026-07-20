package com.c203.limit.domain.stock.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "StockReservationReleaseResponse", description = "재고 복구 결과")
public class StockReservationReleaseResponse {

    @Schema(description = "재고 예약 ID", example = "901", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long reservationId;

    @Schema(description = "예약 상태", example = "RELEASED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "복구 수량", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer releasedQuantity;

    @Schema(description = "복구 후 가용 수량", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer availableQuantity;

    @Schema(description = "복구 시각", example = "2026-07-16T10:06:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime releasedAt;
}
