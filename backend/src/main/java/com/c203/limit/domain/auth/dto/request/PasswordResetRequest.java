package com.c203.limit.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PasswordResetRequest", description = "비밀번호 재설정 메일 발송 요청")
public record PasswordResetRequest(
        @Schema(description = "가입 이메일", example = "member@example.com") String email) {}
