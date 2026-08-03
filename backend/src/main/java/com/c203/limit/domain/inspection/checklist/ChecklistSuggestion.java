package com.c203.limit.domain.inspection.checklist;

import com.c203.limit.domain.inspection.enums.EvidenceType;

public record ChecklistSuggestion(
        String featureCode,
        String featureName,
        ChecklistEvidenceStatus evidenceStatus,
        String reason,
        String checkGuide,
        String sourceUrl,
        String sourceTitle,
        EvidenceType evidenceType) {

    public ChecklistSuggestion(
            String featureCode,
            String featureName,
            ChecklistEvidenceStatus evidenceStatus,
            String reason,
            String checkGuide,
            String sourceUrl,
            String sourceTitle) {
        this(
                featureCode,
                featureName,
                evidenceStatus,
                reason,
                checkGuide,
                sourceUrl,
                sourceTitle,
                null);
    }

    public ChecklistSuggestion withEvidenceType(EvidenceType resolvedEvidenceType) {
        return new ChecklistSuggestion(
                featureCode,
                featureName,
                evidenceStatus,
                reason,
                checkGuide,
                sourceUrl,
                sourceTitle,
                resolvedEvidenceType);
    }
}
