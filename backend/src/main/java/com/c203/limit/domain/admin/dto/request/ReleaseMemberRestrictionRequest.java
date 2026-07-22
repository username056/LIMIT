package com.c203.limit.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "ReleaseMemberRestrictionRequest", description = "회원 제재 해제 요청")
public class ReleaseMemberRestrictionRequest {

    @Schema(description = "제재 해제 사유", example = "오탐 확인 후 해제", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String releaseReason;
}
