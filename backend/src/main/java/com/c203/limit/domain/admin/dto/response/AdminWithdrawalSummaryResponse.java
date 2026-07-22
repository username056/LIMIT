package com.c203.limit.domain.admin.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminWithdrawalSummaryResponse", description = "관리자 탈퇴 요청 목록 항목")
public class AdminWithdrawalSummaryResponse {

    @Schema(description = "탈퇴 요청 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long withdrawalRequestId;

    @Schema(description = "회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long memberId;

    @Schema(description = "마스킹 이메일", example = "u***@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String memberEmail;

    @Schema(description = "처리 상태", example = "REQUESTED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "요청 시각", example = "2026-07-16T11:40:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime requestedAt;
}
