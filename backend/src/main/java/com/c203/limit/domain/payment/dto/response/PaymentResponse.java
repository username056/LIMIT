package com.c203.limit.domain.payment.dto.response;

import com.c203.limit.domain.payment.entity.Payment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    @Schema(description = "Toss 결제창에 전달할 PG 주문 식별자", example = "PAY-5001-1")
    private final String providerOrderId;

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

    @Schema(nullable = true, description = "결제 실패·거절 사유", example = "카드 사용이 거절되었습니다.")
    private final String failedReason;

    @Schema(example = "2026-07-27T10:00:00+09:00")
    private final OffsetDateTime requestedAt;

    @Schema(nullable = true, example = "2026-07-27T10:01:00+09:00")
    private final OffsetDateTime approvedAt;

    private static final ZoneId PAYMENT_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getListingId(),
                payment.getProviderOrderId(),
                payment.getStatus().name(),
                payment.getMethod() == null ? null : payment.getMethod().name(),
                payment.getAttemptNo(),
                payment.getRequestedAmount(),
                payment.getApprovedAmount(),
                payment.getFailedReason(),
                offset(payment.getRequestedAt()),
                offset(payment.getApprovedAt()));
    }

    private static OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atZone(PAYMENT_TIME_ZONE).toOffsetDateTime();
    }
}
