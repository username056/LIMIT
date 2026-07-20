package com.c203.limit.inquiry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "CreateInquiryRequest", description = "문의 등록 요청")
public class CreateInquiryRequest {

    @Schema(description = "문의 유형", example = "ACCOUNT", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String category;

    @Schema(description = "문의 제목", example = "로그인 관련 문의", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String title;

    @Schema(description = "문의 상세 내용", example = "로그인이 반복해서 실패합니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String content;
}
