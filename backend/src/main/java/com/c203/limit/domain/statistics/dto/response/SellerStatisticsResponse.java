package com.c203.limit.domain.statistics.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "SellerStatisticsResponse", description = "판매자 통계 응답")
public class SellerStatisticsResponse {

    @Schema(description = "판매자 ID", example = "55", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long sellerId;

    @Schema(description = "DAILY | WEEKLY | MONTHLY", example = "DAILY", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String periodType;

    @Schema(description = "집계 기간 시작일", example = "2026-07-15", requiredMode = Schema.RequiredMode.REQUIRED)
    private final LocalDate periodStart;

    @Schema(description = "판매량", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer salesCount;

    @Schema(description = "환불률(%)", example = "2.4", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal refundRate;

    @Schema(description = "분쟁률(%)", example = "0.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal disputeRate;

    @Schema(description = "배치 집계 실행일시", example = "2026-07-16T03:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant aggregatedAt;
}
