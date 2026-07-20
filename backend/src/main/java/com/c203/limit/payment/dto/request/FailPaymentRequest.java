package com.c203.limit.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "FailPaymentRequest", description = "결제 실패 처리 요청")
public class FailPaymentRequest {

    @Schema(description = "실패 코드", example = "USER_CANCEL", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String failureCode;

    @Schema(description = "실패 메시지", example = "사용자가 결제를 취소했습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String failureMessage;
}
