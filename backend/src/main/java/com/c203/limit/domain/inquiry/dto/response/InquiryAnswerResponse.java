package com.c203.limit.domain.inquiry.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "InquiryAnswerResponse", description = "문의 답변")
public class InquiryAnswerResponse {

    @Schema(description = "답변 ID", example = "2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Long answerId;

    @Schema(description = "답변 버전", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int version;

    @Schema(description = "답변 내용", example = "로그인 제한을 해제했습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String content;

    @Schema(description = "최신 답변 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean isCurrent;

    @Schema(description = "답변 관리자 ID", example = "9001", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Long adminMemberId;

    @Schema(description = "답변 작성 시각", example = "2026-07-16T13:10:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime createdAt;
}
