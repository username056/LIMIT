package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "선택한 조건에서 실제 고를 수 있는 다음 옵션")
public record DeviceModelOptionsResponse(
        @Schema(description = "기기 모델 ID", example = "12") Long deviceModelId,
        @Schema(description = "모델명", example = "Galaxy Book4") String modelName,
        @Schema(description = "모델 코드", example = "NT750XGK") String modelCode,
        @Schema(
                        description =
                                "선택 조건으로 조합이 하나로 좁혀졌을 때의 variant ID. 아직 좁혀지지 않았으면 null",
                        example = "84")
                Long selectedVariantId,
        @Schema(description = "축별로 선택 가능한 값") List<DeviceVariantAxisResponse> axes,
        @Schema(description = "현재 선택 조건을 만족하는 조합") List<DeviceVariantResponse> matchedVariants) {}
