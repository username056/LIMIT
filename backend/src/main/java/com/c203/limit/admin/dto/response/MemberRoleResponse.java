package com.c203.limit.admin.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "MemberRoleResponse", description = "회원 역할 부여 결과")
public class MemberRoleResponse {

    @Schema(description = "회원 역할 관계 ID", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long memberRoleId;

    @Schema(description = "회원 ID", example = "9002", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long memberId;

    @Schema(description = "부여된 역할 코드", example = "OPERATOR", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String roleCode;

    @Schema(description = "역할 부여 시각", example = "2026-07-16T18:10:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime grantedAt;
}
