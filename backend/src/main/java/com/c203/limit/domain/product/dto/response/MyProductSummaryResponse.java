package com.c203.limit.domain.product.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "MyProductSummaryResponse", description = "내 상품 목록 항목")
public class MyProductSummaryResponse {

    @Schema(example = "1001")
    private final Long productId;

    @Schema(example = "Galaxy S24 256GB")
    private final String name;

    @Schema(example = "VERIFYING")
    private final String status;

    @Schema(example = "12")
    private final Integer completedItemCount;

    @Schema(example = "18")
    private final Integer requiredItemCount;

    @Schema(example = "2026-07-22T11:00:00+09:00")
    private final OffsetDateTime updatedAt;
}
