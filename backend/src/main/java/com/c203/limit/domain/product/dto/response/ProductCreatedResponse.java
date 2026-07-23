package com.c203.limit.domain.product.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ProductCreatedResponse", description = "상품 초안 등록 결과")
public class ProductCreatedResponse {

    @Schema(example = "1001")
    private final Long productId;

    @Schema(example = "DRAFT")
    private final String status;

    @Schema(example = "101")
    private final Long deviceModelId;

    @Schema(example = "1")
    private final Integer checklistVersion;

    @Schema(example = "18")
    private final Integer requiredItemCount;

    @Schema(example = "0")
    private final Integer completedItemCount;

    @Schema(example = "2026-07-22T10:00:00+09:00")
    private final OffsetDateTime createdAt;
}
