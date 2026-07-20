package com.c203.limit.payment.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "PaymentReadyResponse", description = "결제 요청 생성 응답")
public class PaymentReadyResponse {

    @Schema(description = "결제 시도 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long paymentId;

    @Schema(description = "주문 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long orderId;

    @Schema(description = "결제대행사", example = "TOSS", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String pgProvider;

    @Schema(description = "요청 금액", example = "123000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal requestedAmount;

    @Schema(description = "결제 상태", example = "REQUESTED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "PG 결제창 초기화 키", example = "test_ck_...", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String clientKey;

    @Schema(description = "외부 노출용 주문번호", example = "ORD-20260716-0001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String orderNumber;
}
