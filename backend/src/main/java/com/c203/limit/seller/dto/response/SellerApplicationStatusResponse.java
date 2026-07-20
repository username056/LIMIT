package com.c203.limit.seller.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "SellerApplicationStatusResponse", description = "판매자 신청 상태 변경 결과")
public class SellerApplicationStatusResponse {

    @Schema(description = "신청 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long applicationId;

    @Schema(description = "변경된 신청 상태", example = "SUBMITTED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "제출 시각", example = "2026-07-16T13:00:00+09:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime submittedAt;

    @Schema(description = "상태 변경 시각", example = "2026-07-16T13:05:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime updatedAt;
}
