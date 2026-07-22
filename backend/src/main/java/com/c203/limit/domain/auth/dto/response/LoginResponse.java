package com.c203.limit.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "LoginResponse", description = "일반 회원 로그인 결과")
public class LoginResponse {

    @Schema(description = "API 인증용 Access Token", example = "eyJ...", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String accessToken;

    @Schema(description = "토큰 재발급용 Refresh Token", example = "eyJ...", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String refreshToken;

    @Schema(description = "토큰 타입", example = "Bearer", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String tokenType;

    @Schema(description = "Access Token 만료까지 남은 초", example = "1800", requiredMode = Schema.RequiredMode.REQUIRED)
    private final long expiresIn;

    @Schema(description = "로그인 회원 요약 정보", example = "-", requiredMode = Schema.RequiredMode.REQUIRED)
    private final com.c203.limit.domain.member.dto.response.MemberSummaryResponse member;
}
