package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "선택 축과 그 축에서 아직 고를 수 있는 값")
public record DeviceVariantAxisResponse(
        @Schema(description = "축 코드", example = "STORAGE_GB") String axis,
        @Schema(
                        description = "선택 가능한 값. 다른 축의 현재 선택을 만족하는 조합에서만 뽑는다",
                        example = "[\"256\", \"512\"]")
                List<String> values,
        @Schema(description = "이 축에 현재 선택된 값. 없으면 null", example = "256") String selected) {}
