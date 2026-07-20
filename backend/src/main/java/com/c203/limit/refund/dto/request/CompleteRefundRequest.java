package com.c203.limit.refund.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "CompleteRefundRequest", description = "환불 처리 결과 반영 요청(PG 콜백 또는 내부 시스템 호출)")
public class CompleteRefundRequest {

    @Schema(description = "환불 처리 결과(COMPLETED | FAILED)", example = "COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String result;
}
