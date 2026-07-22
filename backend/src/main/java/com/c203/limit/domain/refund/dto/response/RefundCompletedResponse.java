package com.c203.limit.domain.refund.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "RefundCompletedResponse", description = "환불 처리 결과 반영 응답")
public class RefundCompletedResponse {

    @Schema(description = "환불 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long refundId;

    @Schema(description = "환불 상태", example = "COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "환불 완료 시각", example = "2026-07-16T12:35:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant completedAt;

    @Schema(description = "최종 주문 상태", example = "CANCELLED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String orderStatus;

    @Schema(description = "재고 복구 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean isInventoryRestored;
}
