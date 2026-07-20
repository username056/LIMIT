package com.c203.limit.admin.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "ApproveSellerApplicationRequest", description = "판매자 신청 승인 요청")
public class ApproveSellerApplicationRequest {

    @Schema(description = "초기 상품 등록 한도", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int productLimit;

    @Schema(description = "초기 판매 금액 한도", example = "10000000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal salesAmountLimit;
}
