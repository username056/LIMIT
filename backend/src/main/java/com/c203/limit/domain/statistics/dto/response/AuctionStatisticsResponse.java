package com.c203.limit.domain.statistics.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AuctionStatisticsResponse", description = "경매 통계 응답")
public class AuctionStatisticsResponse {

    @Schema(description = "경매 ID(drop_event.drop_id와 동일값)", example = "5003", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long auctionId;

    @Schema(description = "총 입찰 수", example = "128", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer bidCount;

    @Schema(description = "평균 입찰가", example = "412000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal avgBidPrice;

    @Schema(description = "낙찰가, 종료 전이면 null", example = "530000", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final BigDecimal finalPrice;

    @Schema(description = "배치 집계 실행일시, 집계 전이면 null", example = "2026-07-16T09:00:00+09:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Instant aggregatedAt;
}
