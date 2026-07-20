package com.c203.limit.admin.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminWithdrawalSearchRequest", description = "관리자 탈퇴 요청 검색 조건")
public class AdminWithdrawalSearchRequest {

    @Schema(description = "페이지 번호", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int page;

    @Schema(description = "페이지 크기", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int size;

    @Schema(description = "탈퇴 요청 상태", example = "REQUESTED", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String status;

    @Schema(description = "요청일 시작", example = "2026-07-01", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final LocalDate requestedFrom;

    @Schema(description = "요청일 종료", example = "2026-07-31", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final LocalDate requestedTo;
}
