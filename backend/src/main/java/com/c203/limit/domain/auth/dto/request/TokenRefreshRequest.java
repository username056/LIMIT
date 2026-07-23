package com.c203.limit.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "TokenRefreshRequest", description = "인증 토큰 재발급 요청")
public class TokenRefreshRequest {

    @Schema(
            description = "유효한 Refresh Token",
            example = "eyJ...",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final String refreshToken;
}
