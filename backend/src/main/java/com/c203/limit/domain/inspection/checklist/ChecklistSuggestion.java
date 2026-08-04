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
        EvidenceType evidenceType,
        String itemCode) {

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
                null,
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
                resolvedEvidenceType,
                itemCode);
    }

    /** 이 feature를 선택했을 때 실제로 생성될 체크리스트 항목의 코드. 카탈로그/정책에서 결정론적으로 파생된다. */
    public ChecklistSuggestion withItemCode(String resolvedItemCode) {
        return new ChecklistSuggestion(
                featureCode,
                featureName,
                evidenceStatus,
                reason,
                checkGuide,
                sourceUrl,
                sourceTitle,
                evidenceType,
                resolvedItemCode);
    }
}
