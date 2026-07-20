package com.c203.limit.domain.product.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "ProductActionResponse", description = "상품 조치(숨김/판매중지/해제) 결과 및 이력 항목")
public class ProductActionResponse {

    @Schema(description = "조치 이력 ID", example = "301", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long actionId;

    @Schema(description = "대상 상품 ID", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long productId;

    @Schema(description = "HIDE | SUSPEND | RELEASE", example = "SUSPEND", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String actionType;

    @Schema(description = "처리 사유", example = "정품 증빙 자료 진위 확인 불가로 판매 중지 처리합니다.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String reason;

    @Schema(description = "처리 관리자 ID, 셀러 자가 처리면 null", example = "7", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Long adminId;

    @Schema(description = "처리 관리자명, 셀러 자가 처리면 null", example = "operator_kim", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String adminName;

    @Schema(description = "처리일시", example = "2026-07-16T11:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant createdAt;
}
