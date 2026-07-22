package com.c203.limit.domain.seller.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "SellerApplicationSummaryResponse", description = "판매자 신청 목록 항목")
public class SellerApplicationSummaryResponse {

    @Schema(description = "신청 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long applicationId;

    @Schema(description = "신청 버전", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int applicationVersion;

    @Schema(description = "판매자 유형", example = "INDIVIDUAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String sellerType;

    @Schema(description = "신청 상태", example = "UNDER_REVIEW", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "제출 시각", example = "2026-07-16T13:00:00+09:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime submittedAt;

    @Schema(description = "심사 완료 시각", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime reviewedAt;
}
