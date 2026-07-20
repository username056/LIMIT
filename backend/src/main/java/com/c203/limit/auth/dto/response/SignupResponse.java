package com.c203.limit.auth.dto.response;

import java.time.OffsetDateTime;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "SignupResponse", description = "회원가입 결과")
public class SignupResponse {

    @Schema(description = "생성된 회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long memberId;

    @Schema(description = "가입 이메일", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String email;

    @Schema(description = "가입 닉네임", example = "openrunner", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String nickname;

    @Schema(description = "회원 상태", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "회원 역할 목록", example = "[BUYER]", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Set<String> roles;

    @Schema(description = "가입 시각", example = "2026-07-16T11:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime createdAt;
}
