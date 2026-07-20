package com.c203.limit.queue.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "CaptchaVerifyResponse", description = "CAPTCHA 검증 결과")
public class CaptchaVerifyResponse {

    @Schema(description = "검증 성공 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Boolean isVerified;

    @Schema(description = "대기열 입장용 단기 토큰", example = "short-lived-verification-token", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String verificationToken;

    @Schema(description = "검증 토큰 만료 시각", example = "2026-07-16T10:02:00+09:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime expiresAt;
}
