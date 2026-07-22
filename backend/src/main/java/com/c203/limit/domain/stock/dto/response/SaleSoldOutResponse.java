package com.c203.limit.domain.stock.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "SaleSoldOutResponse", description = "드롭 품절 상태 변경 결과")
public class SaleSoldOutResponse {

    @Schema(description = "판매 ID", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long saleId;

    @Schema(description = "변경 전 판매 상태", example = "OPEN", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String previousStatus;

    @Schema(description = "변경 후 판매 상태", example = "SOLD_OUT", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "품절 처리 시각", example = "2026-07-16T10:05:01+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime soldOutAt;
}
