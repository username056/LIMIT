package com.c203.limit.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "LoginRequest", description = "일반 회원 로그인 요청")
public class LoginRequest {

    @Schema(
            description = "로그인 이메일",
            example = "user@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final String email;

    @Schema(
            description = "비밀번호",
            example = "Password123!",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final String password;
}
