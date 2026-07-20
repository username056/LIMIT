package com.c203.limit.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminInquiryDetailResponse", description = "관리자 문의 상세")
public class AdminInquiryDetailResponse {

    @Schema(description = "문의 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long inquiryId;

    @Schema(description = "작성 회원 요약", example = "-", requiredMode = Schema.RequiredMode.REQUIRED)
    private final AdminMemberSummaryResponse member;

    @Schema(description = "문의 유형", example = "ACCOUNT", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String category;

    @Schema(description = "문의 제목", example = "로그인 관련 문의", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String title;

    @Schema(description = "문의 내용", example = "로그인이 반복해서 실패합니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String content;

    @Schema(description = "처리 상태", example = "IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "답변 버전 이력", example = "[]", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<com.c203.limit.inquiry.dto.response.InquiryAnswerResponse> answers;

    @Schema(description = "접수 시각", example = "2026-07-16T12:10:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime createdAt;

    @Schema(description = "종료 시각", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime closedAt;
}
