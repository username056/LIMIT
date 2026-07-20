package com.c203.limit.admin.dto.request;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "UpdateSellerStatusRequest", description = "판매자 상태 변경 요청")
public class UpdateSellerStatusRequest {

    @Schema(description = "변경할 판매자 상태", example = "SUSPENDED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String sellerStatus;

    @Schema(description = "상태 변경 사유", example = "가품 판매 의심 조사 중", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String reason;

    @Schema(description = "기간성 정지 종료 시각", example = "2026-08-16T00:00:00+09:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime endsAt;
}
