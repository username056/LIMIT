package com.c203.limit.domain.inquiry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "UpdateInquiryRequest", description = "답변 전 문의 수정 요청")
public class UpdateInquiryRequest {

    @Schema(description = "수정할 제목", example = "로그인 제한 문의", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String title;

    @Schema(description = "수정할 본문", example = "현재도 로그인할 수 없습니다.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String content;
}
