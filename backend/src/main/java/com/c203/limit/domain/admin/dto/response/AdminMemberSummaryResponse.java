package com.c203.limit.domain.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminMemberSummaryResponse", description = "관리자 회원 목록 항목")
public class AdminMemberSummaryResponse {

    @Schema(description = "회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long memberId;

    @Schema(description = "마스킹 이메일", example = "u***@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String email;

    @Schema(description = "닉네임", example = "openrunner", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String nickname;

    @Schema(description = "회원 상태", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "인증 방식", example = "LOCAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String authType;

    @Schema(description = "역할 목록", example = "[BUYER]", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Set<String> roles;

    @Schema(description = "활성 제재 수", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int activeRestrictionCount;

    @Schema(description = "가입 시각", example = "2026-07-01T10:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime createdAt;
}
