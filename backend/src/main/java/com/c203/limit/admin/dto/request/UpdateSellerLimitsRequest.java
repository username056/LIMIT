package com.c203.limit.admin.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "UpdateSellerLimitsRequest", description = "판매자 한도 조정 요청")
public class UpdateSellerLimitsRequest {

    @Schema(description = "변경할 상품 등록 한도", example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int productLimit;

    @Schema(description = "변경할 판매 금액 한도", example = "50000000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal salesAmountLimit;

    @Schema(description = "한도 조정 사유", example = "정상 거래 누적에 따른 한도 상향", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String reason;
}
