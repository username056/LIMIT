package com.c203.limit.admin.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "InquiryStatusResponse", description = "문의 상태 변경 결과")
public class InquiryStatusResponse {

    @Schema(description = "문의 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long inquiryId;

    @Schema(description = "변경된 문의 상태", example = "CLOSED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "문의 종료 시각", example = "2026-07-16T18:00:00+09:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime closedAt;
}
