package com.c203.limit.domain.auth.dto.response;

import com.c203.limit.domain.member.dto.response.MemberSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "SocialLoginResponse", description = "소셜 로그인 또는 최초 가입 전환 결과")
public class SocialLoginResponse {
    @Schema(
            description = "AUTHENTICATED 또는 SIGNUP_REQUIRED",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "기존 회원 로그인 시 Access Token")
    private final String accessToken;

    @Schema(description = "토큰 타입", example = "Bearer")
    private final String tokenType;

    @Schema(description = "Access Token 만료까지 남은 초", example = "1800")
    private final Long expiresIn;

    @Schema(description = "로그인 회원 요약")
    private final MemberSummaryResponse member;

    @Schema(description = "최초 가입 화면 표시 정보")
    private final SocialSignupPreviewResponse signup;
}
