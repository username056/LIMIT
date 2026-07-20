package com.c203.limit.domain.admin.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminActionLogSearchRequest", description = "관리자 감사 로그 검색 조건")
public class AdminActionLogSearchRequest {

    @Schema(description = "페이지 번호", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int page;

    @Schema(description = "페이지 크기", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int size;

    @Schema(description = "처리 관리자 ID", example = "9001", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Long adminMemberId;

    @Schema(description = "관리 작업 유형", example = "MEMBER_RESTRICT", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String actionType;

    @Schema(description = "작업 대상 유형", example = "MEMBER", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String targetType;

    @Schema(description = "작업 대상 ID", example = "1", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Long targetId;

    @Schema(description = "처리일 시작", example = "2026-07-01", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final LocalDate createdFrom;

    @Schema(description = "처리일 종료", example = "2026-07-31", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final LocalDate createdTo;
}
