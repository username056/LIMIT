package com.c203.limit.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UpdateAdminAccountAccessRequest", description = "관리자 권한·상태 변경 요청")
public record UpdateAdminAccountAccessRequest(
        @Schema(example = "SUPER_ADMIN") String role, @Schema(example = "ACTIVE") String status) {}
