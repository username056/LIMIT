package com.c203.limit.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "OrderDetailResponse", description = "주문 상세 조회 응답. 본인이 구매자 또는 판매자인 주문만 조회 가능")
public class OrderDetailResponse {

    @Schema(description = "주문 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long orderId;

    @Schema(description = "외부 노출용 주문번호", example = "ORD-20260716-0001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String orderNumber;

    @Schema(description = "구매자 회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long buyerId;

    @Schema(description = "판매자 회원 ID", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long sellerId;

    @Schema(description = "주문 상품 ID", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long productId;

    @Schema(description = "주문 수량", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int quantity;

    @Schema(description = "주문 금액", example = "123000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal amount;

    @Schema(description = "주문 상태", example = "PAID", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "취소 사유", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String cancelReason;

    @Schema(description = "구매 확정 시각", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Instant confirmedAt;

    @Schema(description = "취소 시각", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Instant cancelledAt;

    @Schema(description = "생성 시각", example = "2026-07-16T11:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant createdAt;

    @Schema(description = "마지막 수정 시각", example = "2026-07-16T11:05:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant updatedAt;
}
