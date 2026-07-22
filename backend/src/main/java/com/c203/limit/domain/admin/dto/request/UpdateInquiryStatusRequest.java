package com.c203.limit.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "UpdateInquiryStatusRequest", description = "문의 처리 상태 변경 요청")
public class UpdateInquiryStatusRequest {

    @Schema(description = "변경할 문의 상태", example = "CLOSED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;
}
