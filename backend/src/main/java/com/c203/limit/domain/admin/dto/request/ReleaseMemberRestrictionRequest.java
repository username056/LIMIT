package com.c203.limit.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ReleaseMemberRestrictionRequest", description = "회원 제재 해제 요청")
public record ReleaseMemberRestrictionRequest(
        @NotBlank @Schema(example = "오탐 확인 후 해제") String releaseReason) {}
