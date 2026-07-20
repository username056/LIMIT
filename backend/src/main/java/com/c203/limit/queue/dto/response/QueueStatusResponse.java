package com.c203.limit.queue.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "QueueStatusResponse", description = "내 대기 순번 및 예상 입장 정보")
public class QueueStatusResponse {

    @Schema(description = "대기열 참여 ID", example = "1201", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long queueParticipationId;

    @Schema(description = "판매 ID", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long saleId;

    @Schema(description = "발급 순번", example = "532", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long sequenceNo;

    @Schema(description = "대기열 상태", example = "WAITING", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "앞 대기 인원", example = "27", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long aheadCount;

    @Schema(description = "예상 입장 시각", example = "2026-07-16T10:03:00+09:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime estimatedAdmissionAt;

    @Schema(description = "발급 구매권", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final PurchasePassSummaryResponse purchasePass;

    @Schema(description = "조회 기준 시각", example = "2026-07-16T10:01:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime checkedAt;
}
