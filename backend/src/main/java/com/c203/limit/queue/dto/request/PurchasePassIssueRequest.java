package com.c203.limit.queue.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "PurchasePassIssueRequest", description = "구매권 발급 요청")
public class PurchasePassIssueRequest {

    @Schema(description = "대기열 참여 ID", example = "1201", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long queueParticipationId;

    @Schema(description = "발급 대상 사용자 ID", example = "35", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long userId;

    @Schema(description = "구매권 유효시간(초)", example = "300", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer expiresInSeconds;
}
