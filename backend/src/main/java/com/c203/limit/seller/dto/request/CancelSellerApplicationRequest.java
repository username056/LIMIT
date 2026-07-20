package com.c203.limit.seller.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "CancelSellerApplicationRequest", description = "판매자 신청 취소 요청")
public class CancelSellerApplicationRequest {

    @Schema(description = "신청 취소 사유", example = "신청 정보 재작성", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String reason;
}
