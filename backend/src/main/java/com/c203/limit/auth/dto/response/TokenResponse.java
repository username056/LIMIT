package com.c203.limit.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "TokenResponse", description = "토큰 재발급 결과")
public class TokenResponse {

    @Schema(description = "새 Access Token", example = "eyJ...", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String accessToken;

    @Schema(description = "Rotation된 새 Refresh Token", example = "eyJ...", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String refreshToken;

    @Schema(description = "토큰 타입", example = "Bearer", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String tokenType;

    @Schema(description = "Access Token 만료까지 남은 초", example = "1800", requiredMode = Schema.RequiredMode.REQUIRED)
    private final long expiresIn;
}
