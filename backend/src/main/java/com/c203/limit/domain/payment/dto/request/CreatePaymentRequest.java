package com.c203.limit.domain.payment.dto.request;

import com.c203.limit.domain.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "CreatePaymentRequest", description = "결제 요청 생성 요청")
public class CreatePaymentRequest {

    @Schema(description = "결제 대상 매물 ID", example = "1001")
    @NotNull
    private final Long listingId;

    @Schema(description = "결제 수단", example = "CARD")
    @NotNull
    private final PaymentMethod method;

    @Schema(description = "클라이언트가 생성한 멱등키. 동일 키로 재요청하면 기존 결제 요청을 그대로 반환한다.",
            example = "b3f1e6b0-6e3a-4e2a-9c3a-2f6a7d0e5c11")
    @NotBlank
    @Size(max = 100)
    private final String idempotencyKey;
}
