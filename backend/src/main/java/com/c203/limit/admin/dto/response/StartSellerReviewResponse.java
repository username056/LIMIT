package com.c203.limit.admin.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "StartSellerReviewResponse", description = "판매자 신청 심사 시작 결과")
public class StartSellerReviewResponse {

    @Schema(description = "신청 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long applicationId;

    @Schema(description = "변경 상태", example = "UNDER_REVIEW", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "심사 담당 관리자 ID", example = "9001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long reviewerMemberId;

    @Schema(description = "심사 시작 시각", example = "2026-07-16T16:15:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime updatedAt;
}
