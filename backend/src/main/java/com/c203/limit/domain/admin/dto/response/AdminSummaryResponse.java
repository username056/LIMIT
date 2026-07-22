package com.c203.limit.domain.admin.dto.response;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminSummaryResponse", description = "관리자 요약 정보")
public class AdminSummaryResponse {

    @Schema(description = "관리자 회원 ID", example = "9001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long memberId;

    @Schema(description = "관리자 닉네임", example = "operator", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String nickname;

    @Schema(description = "관리자 역할 목록", example = "[OPERATOR]", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Set<String> roles;
}
