package com.c203.limit.domain.inspection.checklist;

public record ChecklistSuggestion(
        LaptopFeatureCode featureCode,
        ChecklistEvidenceStatus evidenceStatus,
        String reason,
        String checkGuide,
        String sourceUrl,
        String sourceTitle) {}
