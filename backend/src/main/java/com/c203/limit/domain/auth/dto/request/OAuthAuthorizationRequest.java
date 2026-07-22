package com.c203.limit.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "OAuthAuthorizationRequest", description = "OAuth 인가 시작 요청")
public record OAuthAuthorizationRequest(
        @Schema(description = "등록된 프론트 콜백 URI") String redirectUri) {}
