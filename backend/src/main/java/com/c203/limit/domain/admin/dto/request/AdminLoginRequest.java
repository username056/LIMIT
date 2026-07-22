package com.c203.limit.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "AdminLoginRequest", description = "관리자 로그인 요청")
public record AdminLoginRequest(
        @Email @NotBlank @Schema(example = "admin@example.com") String email,
        @NotBlank @Schema(example = "AdminPassword123!") String password) {}
