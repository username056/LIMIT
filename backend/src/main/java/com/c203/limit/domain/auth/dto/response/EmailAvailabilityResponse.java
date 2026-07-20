package com.c203.limit.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "EmailAvailabilityResponse", description = "이메일 중복 확인 결과")
public class EmailAvailabilityResponse {

    @Schema(description = "확인한 이메일", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String email;

    @Schema(description = "사용 가능 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean isAvailable;
}
