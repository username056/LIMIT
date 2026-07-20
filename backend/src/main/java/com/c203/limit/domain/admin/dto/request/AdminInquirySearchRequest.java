package com.c203.limit.domain.admin.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminInquirySearchRequest", description = "관리자 문의 검색 조건")
public class AdminInquirySearchRequest {

    @Schema(description = "페이지 번호", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int page;

    @Schema(description = "페이지 크기", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int size;

    @Schema(description = "문의 상태", example = "OPEN", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String status;

    @Schema(description = "문의 유형", example = "ACCOUNT", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String category;

    @Schema(description = "작성 회원 ID", example = "1", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Long memberId;

    @Schema(description = "접수일 시작", example = "2026-07-01", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final LocalDate createdFrom;

    @Schema(description = "접수일 종료", example = "2026-07-31", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final LocalDate createdTo;

    @Schema(description = "정렬 조건", example = "createdAt,asc", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String sort;
}
