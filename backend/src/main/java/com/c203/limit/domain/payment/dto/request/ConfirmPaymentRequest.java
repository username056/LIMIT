package com.c203.limit.domain.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ConfirmPaymentRequest", description = "Toss 결제 승인 요청")
public class ConfirmPaymentRequest {

    @Schema(description = "Toss 결제창이 발급한 결제 키", example = "5EnNZRJGvaBX7zk2yd8ydw2qxpXQwGaJK")
    @NotBlank
    private final String paymentKey;

    @Schema(description = "결제 요청 생성 시 발급된 providerOrderId", example = "PAY-5001-1")
    @NotBlank
    private final String orderId;

    @Schema(description = "Toss 결제창에서 승인된 결제 금액(원)", example = "650000")
    @NotNull
    @Positive
    private final Long amount;
}
