package com.c203.limit.domain.product.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "HandoverGuideResponse", description = "모델별 판매 준비 및 개인정보 초기화 가이드")
public class HandoverGuideResponse {

    @Schema(example = "601")
    private final Long guideId;

    @Schema(example = "101")
    private final Long deviceModelId;

    @Schema(example = "1")
    private final Integer version;

    @Schema(example = "Galaxy S24 판매 준비")
    private final String title;

    private final List<HandoverGuideStepResponse> steps;

    @Schema(example = "서비스는 개인정보의 완전한 삭제를 보증하지 않습니다.")
    private final String disclaimer;
}
