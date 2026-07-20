package com.c203.limit.payment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "PaymentApprovedResponse", description = "결제 승인 완료 응답")
public class PaymentApprovedResponse {

    @Schema(description = "결제 시도 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long paymentId;

    @Schema(description = "주문 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long orderId;

    @Schema(description = "결제 상태", example = "APPROVED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "승인 금액", example = "123000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal approvedAmount;

    @Schema(description = "승인 시각", example = "2026-07-16T12:10:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant approvedAt;
}
