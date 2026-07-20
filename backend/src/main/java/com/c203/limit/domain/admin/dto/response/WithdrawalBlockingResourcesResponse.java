package com.c203.limit.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "WithdrawalBlockingResourcesResponse", description = "탈퇴를 막는 진행 중 자원 요약")
public class WithdrawalBlockingResourcesResponse {

    @Schema(description = "진행 중 주문 수", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int openOrders;

    @Schema(description = "진행 중 분쟁 수", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int openDisputes;

    @Schema(description = "처리 중 환불 수", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int pendingRefunds;

    @Schema(description = "처리 중 정산 수", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int pendingSettlements;
}
