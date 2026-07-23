package com.c203.limit.domain.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "MemberProfileResponse", description = "본인 회원 정보 조회 결과")
public class MemberProfileResponse {

    @Schema(description = "회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long memberId;

    @Schema(
            description = "회원 이메일",
            example = "user@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final String email;

    @Schema(
            description = "닉네임",
            example = "openrunner",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final String nickname;

    @Schema(
            description = "마스킹된 연락처",
            example = "010****5678",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String phone;

    @Schema(description = "회원 상태", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "인증 방식", example = "LOCAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String authType;

    @Schema(
            description = "권한 목록",
            example = "[MEMBER]",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final Set<String> roles;

    @Schema(
            description = "이메일 인증 시각",
            example = "null",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final LocalDateTime emailVerifiedAt;

    @Schema(
            description = "최근 로그인 시각",
            example = "2026-07-16T10:50:00",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final LocalDateTime lastLoginAt;

    @Schema(
            description = "가입 시각",
            example = "2026-07-01T10:00:00",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final LocalDateTime createdAt;
}
