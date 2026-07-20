package com.c203.limit.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminLoginResponse", description = "관리자 로그인 결과")
public class AdminLoginResponse {

    @Schema(description = "관리자 Access Token", example = "eyJ...", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String accessToken;

    @Schema(description = "관리자 Refresh Token", example = "eyJ...", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String refreshToken;

    @Schema(description = "관리자 요약 정보", example = "-", requiredMode = Schema.RequiredMode.REQUIRED)
    private final AdminSummaryResponse admin;
}
