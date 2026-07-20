package com.c203.limit.domain.queue.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "CaptchaVerifyRequest", description = "CAPTCHA 및 기기 정보 검증 요청")
public class CaptchaVerifyRequest {

    @Schema(description = "CAPTCHA 제공자 토큰", example = "provider-captcha-token", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String captchaToken;

    @Schema(description = "기기 식별 해시", example = "hashed-device-fingerprint", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String deviceFingerprint;

    @Schema(description = "검증 대상 작업", example = "QUEUE_JOIN", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String action;
}
