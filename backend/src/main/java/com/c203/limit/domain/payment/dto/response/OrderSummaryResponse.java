package com.c203.limit.domain.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "OrderSummaryResponse", description = "구매자 주문 내역 목록 항목")
public class OrderSummaryResponse {

    @Schema(example = "5001")
    private final Long paymentId;

    @Schema(example = "1001")
    private final Long listingId;

    @Schema(example = "Galaxy S24 256GB")
    private final String productName;

    @Schema(nullable = true, example = "https://cdn.example.com/listings/1001/thumbnail.jpg")
    private final String thumbnailUrl;

    @Schema(example = "650000")
    private final BigDecimal price;

    @Schema(example = "APPROVED")
    private final String paymentStatus;

    @Schema(description = "매물의 현재 상태(결제 시점이 아니라 조회 시점 기준)", example = "PAID")
    private final String listingStatus;

    @Schema(example = "2026-07-27T10:00:00+09:00")
    private final OffsetDateTime requestedAt;

    @Schema(nullable = true, example = "2026-07-27T10:01:00+09:00")
    private final OffsetDateTime approvedAt;
}
