package com.c203.limit.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "OAuthAuthorizationResponse", description = "소셜 로그인 인가 시작 정보")
public record OAuthAuthorizationResponse(
        @Schema(description = "소셜 공급자 인가 화면 URL") String authorizationUrl) {}
