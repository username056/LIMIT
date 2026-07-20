package com.c203.limit.domain.admin.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminSellerApplicationSearchRequest", description = "관리자 판매자 신청 검색 조건")
public class AdminSellerApplicationSearchRequest {

    @Schema(description = "페이지 번호", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int page;

    @Schema(description = "페이지 크기", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int size;

    @Schema(description = "신청 상태", example = "SUBMITTED", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String status;

    @Schema(description = "판매자 유형", example = "INDIVIDUAL", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String sellerType;

    @Schema(description = "국가 코드", example = "US", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String countryCode;

    @Schema(description = "제출일 시작", example = "2026-07-01", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final LocalDate submittedFrom;

    @Schema(description = "제출일 종료", example = "2026-07-31", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final LocalDate submittedTo;

    @Schema(description = "정렬 조건", example = "submittedAt,asc", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String sort;
}
