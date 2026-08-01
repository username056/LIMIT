package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "실제 판매되는 SKU 조합")
public record DeviceVariantResponse(
        @Schema(description = "조합 ID", example = "84") Long variantId,
        @Schema(description = "조합 식별 키", example = "NT750XGK-512") String variantKey,
        @Schema(description = "표시명", example = "Galaxy Book4 512GB") String displayName) {}
