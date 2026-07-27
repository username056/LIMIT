package com.c203.limit.domain.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "CreateRecaptureRequest", description = "구매자의 체크리스트 항목 재촬영 요청")
public class CreateRecaptureRequest {

    @Schema(description = "재촬영 사유 코드", example = "SCREEN_NOT_VISIBLE")
    @NotBlank
    private final String reasonCode;

    @Schema(description = "재촬영 요청 상세 사유", example = "화면 오른쪽 위가 보이지 않습니다.")
    @NotBlank
    @Size(max = 500)
    private final String reason;
}
