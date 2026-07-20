package com.c203.limit.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminMemberDetailResponse", description = "관리자용 회원 상세")
public class AdminMemberDetailResponse {

    @Schema(description = "회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long memberId;

    @Schema(description = "마스킹 이메일", example = "u***@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String email;

    @Schema(description = "닉네임", example = "openrunner", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String nickname;

    @Schema(description = "마스킹 연락처", example = "010****5678", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String phone;

    @Schema(description = "회원 상태", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "인증 방식", example = "LOCAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String authType;

    @Schema(description = "역할 목록", example = "[BUYER]", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Set<String> roles;

    @Schema(description = "연동 소셜 제공자 목록", example = "[GOOGLE]", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Set<String> socialProviders;

    @Schema(description = "활성 배송지 수", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int addressCount;

    @Schema(description = "현재 활성 제재", example = "[]", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<MemberRestrictionResponse> activeRestrictions;

    @Schema(description = "최근 탈퇴 요청", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final com.c203.limit.withdrawal.dto.response.WithdrawalRequestResponse latestWithdrawalRequest;

    @Schema(description = "최근 로그인 시각", example = "2026-07-16T10:50:00+09:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime lastLoginAt;

    @Schema(description = "가입 시각", example = "2026-07-01T10:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime createdAt;
}
