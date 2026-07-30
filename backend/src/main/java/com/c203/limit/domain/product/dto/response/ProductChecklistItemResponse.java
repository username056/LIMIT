package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ProductChecklistItemResponse", description = "상품에 스냅샷된 체크리스트 항목")
public class ProductChecklistItemResponse {

    @Schema(example = "7001")
    private final Long checklistItemId;

    @Schema(example = "SP-DSP-002")
    private final String itemCode;

    @Schema(example = "화면 전체 터치")
    private final String name;

    @Schema(example = "화면 전체 격자를 끊김 없이 드래그하세요.")
    private final String guide;

    @Schema(example = "VIDEO")
    private final String evidenceType;

    @Schema(example = "true")
    private final boolean isRequired;

    @Schema(example = "COMPLETED")
    private final String status;

    @Schema(example = "9001")
    private final Long latestEvidenceId;

    @Schema(example = "1")
    private final Integer attemptCount;

    @Schema(example = "true", description = "구매자에게 항목과 증빙을 공개할지 여부")
    private final boolean visibleToBuyer;

    @Schema(example = "1")
    private final Integer minCount;

    @Schema(example = "3")
    private final Integer maxCount;

    @Schema(example = "20", description = "MiB 단위")
    private final Integer maxFileSizeMb;

    @Schema(example = "3", description = "초 단위")
    private final Integer minDurationSec;

    @Schema(example = "30", description = "초 단위")
    private final Integer maxDurationSec;
}
