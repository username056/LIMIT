package com.c203.limit.domain.queue.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "QueueJoinRequest", description = "대기열 입장 요청")
public class QueueJoinRequest {

    @Schema(description = "CAPTCHA 검증 완료 토큰", example = "captcha-verification-token", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String captchaVerificationToken;

    @Schema(description = "매크로·다계정 탐지용 기기 식별 해시", example = "hashed-device-fingerprint", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String deviceFingerprint;
}
