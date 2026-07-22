package com.c203.limit.domain.queue.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "PurchasePassExpireResponse", description = "구매권 만료 일괄 처리 결과")
public class PurchasePassExpireResponse {

    @Schema(description = "만료 처리 건수", example = "37", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer expiredCount;

    @Schema(description = "재고 복구 건수", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer releasedReservationCount;

    @Schema(description = "다음 대기자 입장 요청 건수", example = "37", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer nextAdmissionRequestedCount;

    @Schema(description = "처리 시각", example = "2026-07-16T10:08:01+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime processedAt;
}
