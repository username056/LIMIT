package com.c203.limit.domain.inquiry.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "InquiryDetailResponse", description = "회원 문의 상세")
public class InquiryDetailResponse {

    @Schema(description = "문의 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long inquiryId;

    @Schema(description = "문의 유형", example = "ACCOUNT", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String category;

    @Schema(description = "제목", example = "로그인 관련 문의", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String title;

    @Schema(description = "본문", example = "로그인이 반복해서 실패합니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String content;

    @Schema(description = "처리 상태", example = "ANSWERED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "등록 시각", example = "2026-07-16T12:10:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime createdAt;

    @Schema(description = "수정 시각", example = "2026-07-16T12:20:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime updatedAt;

    @Schema(description = "최신 답변", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final InquiryAnswerResponse answer;
}
