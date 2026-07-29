package com.c203.limit.domain.inspection.checklist;

public record ChecklistSuggestion(
        LaptopFeatureCode featureCode,
        ChecklistEvidenceStatus evidenceStatus,
        String reason,
        String sourceUrl,
        String sourceTitle) {}
