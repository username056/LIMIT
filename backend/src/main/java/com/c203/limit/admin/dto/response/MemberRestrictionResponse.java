package com.c203.limit.admin.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "MemberRestrictionResponse", description = "회원 제재 응답")
public class MemberRestrictionResponse {

    @Schema(description = "제재 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long restrictionId;

    @Schema(description = "대상 회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long memberId;

    @Schema(description = "제한 기능 범위", example = "PURCHASE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String restrictionType;

    @Schema(description = "제재 상태", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "사유 코드", example = "MACRO_USE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String reasonCode;

    @Schema(description = "상세 사유", example = "비정상 반복 요청 탐지", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String reasonDetail;

    @Schema(description = "시작 시각", example = "2026-07-16T14:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime startsAt;

    @Schema(description = "종료 시각", example = "2026-07-23T14:00:00+09:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime endsAt;

    @Schema(description = "등록 관리자 ID", example = "9001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long createdBy;

    @Schema(description = "해제 관리자 ID", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Long releasedBy;

    @Schema(description = "해제 시각", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime releasedAt;

    @Schema(description = "해제 사유", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String releaseReason;

    @Schema(description = "등록 시각", example = "2026-07-16T13:50:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime createdAt;
}
