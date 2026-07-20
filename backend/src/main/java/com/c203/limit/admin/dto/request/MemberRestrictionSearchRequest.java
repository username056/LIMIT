package com.c203.limit.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "MemberRestrictionSearchRequest", description = "회원 제재 이력 검색 조건")
public class MemberRestrictionSearchRequest {

    @Schema(description = "페이지 번호", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int page;

    @Schema(description = "페이지 크기", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int size;

    @Schema(description = "제재 상태", example = "ACTIVE", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String status;
}
