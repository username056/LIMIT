package com.c203.limit.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ResetPasswordRequest", description = "일회용 토큰을 사용한 비밀번호 재설정")
public record ResetPasswordRequest(
        @Schema(description = "메일로 전달된 일회용 토큰") String token,
        @Schema(description = "새 비밀번호", example = "NewPassword456!") String newPassword) {}
