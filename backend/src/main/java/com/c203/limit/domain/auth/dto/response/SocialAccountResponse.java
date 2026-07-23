package com.c203.limit.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "SocialAccountResponse", description = "소셜 연동 계정 정보")
public class SocialAccountResponse {

    @Schema(description = "소셜 연동 ID", example = "11", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long socialAccountId;

    @Schema(description = "소셜 제공자", example = "GOOGLE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String provider;

    @Schema(
            description = "마스킹된 소셜 이메일",
            example = "u***@gmail.com",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String providerEmail;

    @Schema(
            description = "연동 시각",
            example = "2026-07-01T10:00:00",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private final LocalDateTime connectedAt;
}
