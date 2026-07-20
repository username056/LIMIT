package com.c203.limit.domain.refund.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "RefundSummaryResponse", description = "환불 내역 조회 응답")
public class RefundSummaryResponse {

    @Schema(description = "환불 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long refundId;

    @Schema(description = "환불 금액", example = "123000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal cancelAmount;

    @Schema(description = "환불 상태", example = "FAILED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "환불 요청 시각", example = "2026-07-16T12:30:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant requestedAt;

    @Schema(description = "환불 완료 시각", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Instant completedAt;
}
