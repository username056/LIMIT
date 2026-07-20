package com.c203.limit.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "ChangePasswordRequest", description = "비밀번호 변경 요청")
public class ChangePasswordRequest {

    @Schema(description = "현재 비밀번호", example = "Password123!", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String currentPassword;

    @Schema(description = "변경할 새 비밀번호", example = "NewPassword456!", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String newPassword;
}
