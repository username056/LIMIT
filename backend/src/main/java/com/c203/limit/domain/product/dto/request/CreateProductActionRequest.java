package com.c203.limit.domain.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "CreateProductActionRequest", description = "상품 조치(숨김/판매중지/해제) 생성 요청")
public class CreateProductActionRequest {

    @Schema(description = "HIDE | SUSPEND | RELEASE", example = "SUSPEND", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String actionType;

    @Schema(description = "처리 사유", example = "정품 증빙 자료 진위 확인 불가로 판매 중지 처리합니다.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String reason;
}
