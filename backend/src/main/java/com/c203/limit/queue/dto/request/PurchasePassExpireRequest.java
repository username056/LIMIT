package com.c203.limit.queue.dto.request;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "PurchasePassExpireRequest", description = "만료 구매권 일괄 회수 요청")
public class PurchasePassExpireRequest {

    @Schema(description = "만료 판단 기준 시각", example = "2026-07-16T10:08:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime expiredBefore;

    @Schema(description = "일괄 처리 최대 건수", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer batchSize;
}
