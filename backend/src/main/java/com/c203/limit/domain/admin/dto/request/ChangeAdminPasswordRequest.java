package com.c203.limit.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ChangeAdminPasswordRequest", description = "관리자 본인 비밀번호 변경 요청")
public record ChangeAdminPasswordRequest(
        @NotBlank @Size(max = 72) @Schema(description = "현재 비밀번호", example = "AdminPassword123!")
                String currentPassword,
        @NotBlank @Size(min = 12, max = 72) @Schema(description = "새 비밀번호", example = "NewAdminPassword456!")
                String newPassword) {}
