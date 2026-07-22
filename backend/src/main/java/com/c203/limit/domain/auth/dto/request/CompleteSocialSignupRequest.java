package com.c203.limit.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CompleteSocialSignupRequest", description = "최초 소셜 회원가입 완료 요청")
public record CompleteSocialSignupRequest(
        @Schema(description = "닉네임", example = "openrunner") String nickname,
        @Schema(description = "휴대전화 번호", example = "01012345678") String phone,
        @Schema(description = "서비스 이용약관 동의") boolean serviceTermsAccepted,
        @Schema(description = "개인정보 처리방침 동의") boolean privacyTermsAccepted,
        @Schema(description = "만 14세 이상 동의") boolean ageRequirementAccepted,
        @Schema(description = "마케팅 정보 수신 동의") boolean marketingAccepted) {}
