package com.c203.limit.domain.member.dto.response;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "MemberSummaryResponse", description = "여러 API에서 재사용하는 회원 요약")
public class MemberSummaryResponse {

    @Schema(description = "회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long memberId;

    @Schema(description = "닉네임", example = "openrunner", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String nickname;

    @Schema(description = "역할 목록", example = "[BUYER]", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Set<String> roles;
}
