package com.c203.limit.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "SocialLoginRequest", description = "소셜 로그인 요청")
public class SocialLoginRequest {

    @Schema(description = "OAuth 인가 코드", example = "4/0A...", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String authorizationCode;

    @Schema(description = "OAuth Callback URI", example = "https://.../oauth/callback", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String redirectUri;
}
