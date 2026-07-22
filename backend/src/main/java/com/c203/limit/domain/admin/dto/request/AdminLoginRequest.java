package com.c203.limit.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminLoginRequest", description = "관리자 로그인 요청")
public class AdminLoginRequest {

    @Schema(description = "관리자 계정 이메일", example = "admin@openrun.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String email;

    @Schema(description = "관리자 비밀번호", example = "AdminPassword123!", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String password;
}
