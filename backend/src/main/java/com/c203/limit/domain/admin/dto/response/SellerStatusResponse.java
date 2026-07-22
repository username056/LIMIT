package com.c203.limit.domain.admin.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "SellerStatusResponse", description = "판매자 상태 변경 결과")
public class SellerStatusResponse {

    @Schema(description = "판매자 프로필 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long sellerProfileId;

    @Schema(description = "변경된 판매자 상태", example = "SUSPENDED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String sellerStatus;

    @Schema(description = "변경 시각", example = "2026-07-16T17:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime updatedAt;
}
