package com.c203.limit.domain.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "TransitionProductStatusRequest", description = "상품 거래 상태 전환 요청")
public class TransitionProductStatusRequest {

    @Schema(description = "전환할 상태", example = "ON_SALE")
    @NotBlank
    private final String targetStatus;

    @Schema(description = "상태 전환 사유", example = "필수 체크리스트 완료")
    @Size(max = 200)
    private final String reason;
}
