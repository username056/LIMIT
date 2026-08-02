package com.c203.limit.domain.payment.dto.response;

import com.c203.limit.domain.payment.entity.Payment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "PaymentReconcileResponse", description = "결제 PG 대사(reconcile) 결과")
public class PaymentReconcileResponse {

    @Schema(description = "PG 조회 결과로 로컬 상태가 바뀌었는지 여부", example = "RECOVERED")
    private final PaymentReconcileOutcome outcome;

    private final PaymentResponse payment;

    public static PaymentReconcileResponse of(PaymentReconcileOutcome outcome, Payment payment) {
        return new PaymentReconcileResponse(outcome, PaymentResponse.from(payment));
    }
}
