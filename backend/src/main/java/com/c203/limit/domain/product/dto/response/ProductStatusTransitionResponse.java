package com.c203.limit.domain.product.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ProductStatusTransitionResponse", description = "상품 상태 전환 결과")
public class ProductStatusTransitionResponse {

    @Schema(example = "3001")
    private final Long transitionId;

    @Schema(example = "1001")
    private final Long productId;

    @Schema(example = "VERIFYING")
    private final String previousStatus;

    @Schema(example = "ON_SALE")
    private final String currentStatus;

    @Schema(example = "필수 체크리스트 완료")
    private final String reason;

    @Schema(example = "2026-07-22T12:00:00+09:00")
    private final OffsetDateTime changedAt;
}
