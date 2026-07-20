package com.c203.limit.domain.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "OrderSummaryResponse", description = "주문 목록 조회 응답. 구매자·판매자 역할별 스코프 적용")
public class OrderSummaryResponse {

    @Schema(description = "주문 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long orderId;

    @Schema(description = "외부 노출용 주문번호", example = "ORD-20260716-0001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String orderNumber;

    @Schema(description = "주문 상품명", example = "상품명", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String productName;

    @Schema(description = "주문 금액", example = "123000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal amount;

    @Schema(description = "주문 상태", example = "PAID", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "생성 시각", example = "2026-07-16T11:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant createdAt;
}
