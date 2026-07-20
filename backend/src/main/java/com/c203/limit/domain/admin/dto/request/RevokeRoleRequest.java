package com.c203.limit.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "RevokeRoleRequest", description = "회원 역할 회수 요청")
public class RevokeRoleRequest {

    @Schema(description = "역할 회수 사유", example = "운영 업무 종료", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String reason;
}
