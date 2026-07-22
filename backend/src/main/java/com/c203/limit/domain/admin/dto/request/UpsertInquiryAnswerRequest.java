package com.c203.limit.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "UpsertInquiryAnswerRequest", description = "문의 답변 등록·수정 요청")
public class UpsertInquiryAnswerRequest {

    @Schema(description = "등록하거나 수정할 답변 내용", example = "로그인 시도 제한을 해제했습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String content;
}
