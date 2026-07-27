package com.c203.limit.domain.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ConfirmEvidenceRequest", description = "구매자의 최신 증거 확인 요청")
public class ConfirmEvidenceRequest {

    @Schema(description = "CONFIRMED 또는 UNCERTAIN", example = "CONFIRMED")
    @NotBlank
    private final String status;
}
