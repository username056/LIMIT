package com.c203.limit.domain.admin.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminActionLogSummaryResponse", description = "관리자 감사 로그 목록 항목")
public class AdminActionLogSummaryResponse {

    @Schema(description = "감사 로그 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long adminActionLogId;

    @Schema(description = "처리 관리자 ID", example = "9001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long adminMemberId;

    @Schema(description = "관리 작업 유형", example = "MEMBER_RESTRICT", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String actionType;

    @Schema(description = "대상 유형", example = "MEMBER", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String targetType;

    @Schema(description = "대상 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long targetId;

    @Schema(description = "처리 사유", example = "비정상 반복 요청 탐지", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String reason;

    @Schema(description = "접속 IP", example = "203.0.113.10", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String ipAddress;

    @Schema(description = "처리 시각", example = "2026-07-16T13:50:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime createdAt;
}
