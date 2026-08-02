package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "PurchaseConfirmationResponse", description = "구매확정 결과")
public class PurchaseConfirmationResponse {

    @Schema(example = "1001")
    private final Long productId;

    @Schema(example = "CONFIRMED")
    private final String status;

    @Schema(example = "2026-08-03T12:00:00+09:00")
    private final OffsetDateTime confirmedAt;
}
