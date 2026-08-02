package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PurchaseConfirmationApiResponse", description = "구매확정 공통 응답")
public record PurchaseConfirmationApiResponse(
        PurchaseConfirmationResponse data,
        @Schema(nullable = true) Object meta) {}
