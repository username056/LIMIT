package com.c203.limit.domain.inspection.checklist;

public record ChecklistSuggestion(
        String featureCode,
        String featureName,
        ChecklistEvidenceStatus evidenceStatus,
        String reason,
        String checkGuide,
        String sourceUrl,
        String sourceTitle) {}
