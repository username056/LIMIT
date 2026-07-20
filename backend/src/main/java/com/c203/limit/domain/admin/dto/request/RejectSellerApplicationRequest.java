package com.c203.limit.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "RejectSellerApplicationRequest", description = "판매자 신청 거절 요청")
public class RejectSellerApplicationRequest {

    @Schema(description = "구체적인 거절 사유", example = "사업자 증빙의 식별번호를 확인할 수 없습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String rejectionReason;
}
