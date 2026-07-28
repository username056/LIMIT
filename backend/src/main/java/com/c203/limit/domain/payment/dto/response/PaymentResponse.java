package com.c203.limit.domain.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "PaymentResponse", description = "결제 상세")
public class PaymentResponse {

    @Schema(example = "5001")
    private final Long paymentId;

    @Schema(example = "1001")
    private final Long listingId;

    @Schema(example = "REQUESTED")
    private final String status;

    @Schema(example = "CARD")
    private final String method;

    @Schema(example = "1")
    private final Integer attemptNo;

    @Schema(example = "650000")
    private final BigDecimal requestedAmount;

    @Schema(nullable = true, example = "650000")
    private final BigDecimal approvedAmount;

    @Schema(example = "2026-07-27T10:00:00+09:00")
    private final OffsetDateTime requestedAt;

    @Schema(nullable = true, example = "2026-07-27T10:01:00+09:00")
    private final OffsetDateTime approvedAt;
}
