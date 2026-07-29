package com.c203.limit.domain.inspection.checklist;

import com.c203.limit.domain.product.entity.OsFamily;
import java.util.List;

public record GeneratedChecklist(
        Long deviceModelId,
        String manufacturer,
        String modelName,
        OsFamily osFamily,
        int templateVersion,
        boolean aiApplied,
        List<GeneratedChecklistItem> items,
        List<ChecklistSuggestion> aiSuggestions,
        List<String> reviewCandidates) {

    public GeneratedChecklist {
        items = List.copyOf(items);
        aiSuggestions = List.copyOf(aiSuggestions);
        reviewCandidates = List.copyOf(reviewCandidates);
    }
}
