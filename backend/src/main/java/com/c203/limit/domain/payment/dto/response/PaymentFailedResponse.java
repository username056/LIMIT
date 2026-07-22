package com.c203.limit.domain.payment.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "PaymentFailedResponse", description = "결제 실패 처리 응답")
public class PaymentFailedResponse {

    @Schema(description = "결제 시도 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long paymentId;

    @Schema(description = "결제 상태", example = "FAILED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "실패 사유", example = "사용자가 결제를 취소했습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String failedReason;

    @Schema(description = "주문 상태(재시도 가능하도록 PENDING 유지)", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String orderStatus;

    @Schema(description = "구매권 만료 시각(TTL 내 재시도 가능)", example = "2026-07-16T12:15:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant purchaseRightExpiresAt;

    @Schema(description = "마지막 수정 시각", example = "2026-07-16T12:05:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant updatedAt;
}
