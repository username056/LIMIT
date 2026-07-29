package com.c203.limit.domain.inspection.checklist;

import java.util.List;

public record ChecklistSupplementResult(
        boolean available, List<ChecklistSuggestion> suggestions, List<String> reviewCandidates) {

    public ChecklistSupplementResult {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        reviewCandidates =
                reviewCandidates == null ? List.of() : List.copyOf(reviewCandidates);
    }

    public static ChecklistSupplementResult unavailable() {
        return new ChecklistSupplementResult(false, List.of(), List.of());
    }
}
