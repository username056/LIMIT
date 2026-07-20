package com.c203.limit.statistics.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "DropStatisticsResponse", description = "드롭 통계 응답")
public class DropStatisticsResponse {

    @Schema(description = "드롭 ID", example = "5002", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long dropId;

    @Schema(description = "참여자 수", example = "3204", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer participantCount;

    @Schema(description = "경쟁률(%)", example = "32.04", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal competitionRate;

    @Schema(description = "전환율(%)", example = "18.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal conversionRate;

    @Schema(description = "배치 집계 실행일시, 집계 전이면 null", example = "2026-07-16T09:00:00+09:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Instant aggregatedAt;
}
