package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "HandoverGuideStepResponse", description = "판매 전 계정·개인정보 정리 단계")
public class HandoverGuideStepResponse {

    @Schema(example = "1")
    private final Integer order;

    @Schema(example = "BACKUP_DATA")
    private final String code;

    @Schema(example = "개인정보 백업")
    private final String title;

    @Schema(example = "필요한 사진·연락처·메시지를 백업하세요.")
    private final String description;

    @Schema(example = "true")
    private final boolean isRequired;
}
