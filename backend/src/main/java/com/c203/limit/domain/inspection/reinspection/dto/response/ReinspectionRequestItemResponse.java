package com.c203.limit.domain.inspection.reinspection.dto.response;

import com.c203.limit.domain.inspection.entity.ReinspectionRequestItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ReinspectionRequestItemResponse", description = "재검수 요청 항목")
public class ReinspectionRequestItemResponse {

    @Schema(example = "301")
    private final Long checklistItemId;

    @Schema(example = "제품 외관")
    private final String itemName;

    @Schema(example = "모서리 흠집이 보이도록 가까이 촬영해 주세요.")
    private final String requestContent;

    @Schema(example = "1")
    private final int displayOrder;

    public static ReinspectionRequestItemResponse from(ReinspectionRequestItem item) {
        return new ReinspectionRequestItemResponse(
                item.getListingChecklistItem().getId(),
                item.getItemNameSnapshot(),
                item.getRequestContent(),
                item.getDisplayOrder());
    }
}
