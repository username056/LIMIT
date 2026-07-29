package com.c203.limit.domain.product.dto.response;

import com.c203.limit.domain.inspection.checklist.GeneratedChecklistItem;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "GeneratedChecklistItemResponse", description = "생성된 노트북 체크리스트 항목")
public record GeneratedChecklistItemResponse(
        String itemCode,
        String name,
        String purpose,
        String guide,
        String evidenceType,
        String automationType,
        String parserType,
        boolean required,
        int displayOrder,
        String featureCode,
        String evidenceStatus,
        String sourceUrl,
        String sourceTitle) {

    public static GeneratedChecklistItemResponse from(GeneratedChecklistItem item) {
        return new GeneratedChecklistItemResponse(
                item.itemCode(),
                item.name(),
                item.purpose(),
                item.guide(),
                item.evidenceType().name(),
                item.automationType().name(),
                item.parserType(),
                item.required(),
                item.displayOrder(),
                item.featureCode() == null ? null : item.featureCode().name(),
                item.evidenceStatus() == null ? null : item.evidenceStatus().name(),
                item.sourceUrl(),
                item.sourceTitle());
    }
}
