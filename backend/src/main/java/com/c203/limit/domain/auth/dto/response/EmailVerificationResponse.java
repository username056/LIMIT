package com.c203.limit.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "EmailVerificationResponse", description = "이메일 인증 결과")
public record EmailVerificationResponse(
        @Schema(description = "인증 완료 여부", example = "true") boolean emailVerified,
        @Schema(description = "인증 완료 시각") LocalDateTime emailVerifiedAt) {}
