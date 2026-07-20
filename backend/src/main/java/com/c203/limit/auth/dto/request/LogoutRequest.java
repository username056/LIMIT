package com.c203.limit.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "LogoutRequest", description = "로그아웃 요청")
public class LogoutRequest {

    @Schema(description = "폐기할 Refresh Token", example = "eyJ...", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String refreshToken;
}
