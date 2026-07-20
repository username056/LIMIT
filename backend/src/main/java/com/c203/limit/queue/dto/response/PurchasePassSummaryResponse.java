package com.c203.limit.queue.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "PurchasePassSummaryResponse", description = "순번 조회에 포함되는 구매권 요약")
public class PurchasePassSummaryResponse {

    @Schema(description = "구매권 ID", example = "801", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long purchasePassId;

    @Schema(description = "구매권 상태", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "서명 구매권 토큰", example = "signed-purchase-token", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String purchaseToken;

    @Schema(description = "구매권 만료 시각", example = "2026-07-16T10:08:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime expiresAt;
}
