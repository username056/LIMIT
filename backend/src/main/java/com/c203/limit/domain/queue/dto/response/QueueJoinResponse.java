package com.c203.limit.domain.queue.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "QueueJoinResponse", description = "대기열 입장 및 순번 발급 결과")
public class QueueJoinResponse {

    @Schema(description = "대기열 참여 ID", example = "1201", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long queueParticipationId;

    @Schema(description = "판매 ID", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long saleId;

    @Schema(description = "서버 발급 입장 순번", example = "532", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long sequenceNo;

    @Schema(description = "대기열 상태", example = "WAITING", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "서버 접수 시각", example = "2026-07-16T10:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime joinedAt;
}
