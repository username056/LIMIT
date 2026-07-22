package com.c203.limit.domain.admin.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "WithdrawalProcessResponse", description = "탈퇴 요청 처리 결과")
public class WithdrawalProcessResponse {

    @Schema(description = "탈퇴 요청 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long withdrawalRequestId;

    @Schema(description = "변경된 처리 상태", example = "COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "처리 관리자 ID", example = "9001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long processedBy;

    @Schema(description = "처리 시각", example = "2026-07-16T16:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime processedAt;
}
