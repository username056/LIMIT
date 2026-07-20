package com.c203.limit.admin.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminWithdrawalDetailResponse", description = "관리자 탈퇴 요청 상세")
public class AdminWithdrawalDetailResponse {

    @Schema(description = "탈퇴 요청 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long withdrawalRequestId;

    @Schema(description = "회원 요약", example = "-", requiredMode = Schema.RequiredMode.REQUIRED)
    private final AdminMemberSummaryResponse member;

    @Schema(description = "처리 상태", example = "REQUESTED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "회원 탈퇴 사유", example = "서비스 이용 빈도 감소", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String withdrawalReason;

    @Schema(description = "제한 사유", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String blockedReason;

    @Schema(description = "진행 중 거래 요약", example = "-", requiredMode = Schema.RequiredMode.REQUIRED)
    private final WithdrawalBlockingResourcesResponse blockingResources;

    @Schema(description = "요청 시각", example = "2026-07-16T11:40:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime requestedAt;

    @Schema(description = "처리 시각", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime processedAt;
}
