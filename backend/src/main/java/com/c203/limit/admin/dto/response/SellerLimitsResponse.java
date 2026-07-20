package com.c203.limit.admin.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "SellerLimitsResponse", description = "판매자 한도 조정 결과")
public class SellerLimitsResponse {

    @Schema(description = "판매자 프로필 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long sellerProfileId;

    @Schema(description = "변경된 상품 등록 한도", example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int productLimit;

    @Schema(description = "변경된 판매 금액 한도", example = "50000000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal salesAmountLimit;

    @Schema(description = "변경 시각", example = "2026-07-16T17:10:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime updatedAt;
}
