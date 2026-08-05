package com.c203.limit.domain.inspection.checklist;

import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.EvidenceType;

public record GeneratedChecklistItem(
        String itemCode,
        String name,
        String purpose,
        String guide,
        EvidenceType evidenceType,
        AutomationType automationType,
        String parserType,
        boolean required,
        boolean visibleToBuyer,
        int displayOrder,
        String featureCode,
        ChecklistEvidenceStatus evidenceStatus,
        String sourceUrl,
        String sourceTitle) {

    public GeneratedChecklistItem withDisplayOrder(int order) {
        return new GeneratedChecklistItem(
                itemCode,
                name,
                purpose,
                guide,
                evidenceType,
                automationType,
                parserType,
                required,
                visibleToBuyer,
                order,
                featureCode,
                evidenceStatus,
                sourceUrl,
                sourceTitle);
    }
}
