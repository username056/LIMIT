package com.c203.limit.domain.payment.dto.request;

import com.c203.limit.domain.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "RetryPaymentRequest", description = "결제 재시도 요청")
public class RetryPaymentRequest {

    @Schema(
            description = "재시도 시 사용할 결제 수단. 결제창에서 다른 수단으로 바꿔 재시도할 수 있으므로 "
                    + "기존 결제의 method를 그대로 가정하지 않고 매번 명시적으로 받는다.",
            example = "TOSSPAY")
    @NotNull
    private final PaymentMethod method;
}
