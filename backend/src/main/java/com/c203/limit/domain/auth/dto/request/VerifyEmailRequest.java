package com.c203.limit.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "VerifyEmailRequest", description = "이메일 인증 완료 요청")
public record VerifyEmailRequest(@Schema(description = "메일 링크에 포함된 1회용 토큰") String token) {}
