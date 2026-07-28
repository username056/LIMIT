package com.c203.limit.domain.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PaymentApiResponse", description = "결제 단건 공통 응답")
public record PaymentApiResponse(PaymentResponse data, @Schema(nullable = true) Object meta) {}
