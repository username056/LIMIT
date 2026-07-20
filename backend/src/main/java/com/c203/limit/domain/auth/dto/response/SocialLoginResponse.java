package com.c203.limit.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "SocialLoginResponse", description = "소셜 로그인 결과")
public class SocialLoginResponse {

    @Schema(description = "최초 가입 회원 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean isNewMember;

    @Schema(description = "Access Token", example = "eyJ...", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String accessToken;

    @Schema(description = "Refresh Token", example = "eyJ...", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String refreshToken;

    @Schema(description = "회원 요약 정보", example = "-", requiredMode = Schema.RequiredMode.REQUIRED)
    private final com.c203.limit.domain.member.dto.response.MemberSummaryResponse member;
}
