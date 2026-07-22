package com.c203.limit.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "RoleResponse", description = "역할 정보")
public class RoleResponse {

    @Schema(description = "역할 ID", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long roleId;

    @Schema(description = "역할 코드", example = "OPERATOR", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String roleCode;

    @Schema(description = "화면 표시 역할명", example = "운영 관리자", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String roleName;

    @Schema(description = "역할 설명", example = "회원·판매자 운영 관리", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String description;
}
