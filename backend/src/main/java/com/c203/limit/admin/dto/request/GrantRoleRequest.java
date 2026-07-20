package com.c203.limit.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "GrantRoleRequest", description = "회원 역할 부여 요청")
public class GrantRoleRequest {

    @Schema(description = "부여할 역할 코드", example = "OPERATOR", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String roleCode;

    @Schema(description = "역할 부여 사유", example = "운영 관리자 계정 등록", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String reason;
}
