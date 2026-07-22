package com.c203.limit.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SocialSignupPreviewResponse", description = "소셜 회원가입 화면 표시 정보")
public record SocialSignupPreviewResponse(
        @Schema(description = "소셜 공급자") String provider,
        @Schema(description = "공급자가 확인한 이메일") String email,
        @Schema(description = "추천 닉네임") String suggestedNickname) {}
