package com.c203.limit.domain.inspection.checklist;

import java.util.List;

public record ChecklistSupplementResult(
        boolean available,
        List<ChecklistSuggestion> suggestions,
        List<String> reviewCandidates,
        String failureCode,
        String failureMessage) {

    public ChecklistSupplementResult {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        reviewCandidates =
                reviewCandidates == null ? List.of() : List.copyOf(reviewCandidates);
    }

    public ChecklistSupplementResult(
            boolean available,
            List<ChecklistSuggestion> suggestions,
            List<String> reviewCandidates) {
        this(available, suggestions, reviewCandidates, null, null);
    }

    public static ChecklistSupplementResult unavailable() {
        return unavailable(
                "AI_UNAVAILABLE",
                "AI 조사 결과를 사용할 수 없어 검증된 기본 체크리스트를 적용했습니다.");
    }

    public static ChecklistSupplementResult configurationUnavailable() {
        return unavailable(
                "AI_CONFIGURATION_UNAVAILABLE",
                "AI 조사 기능이 비활성화되었거나 연결 설정을 확인할 수 없습니다.");
    }

    public static ChecklistSupplementResult requestFailed() {
        return unavailable(
                "AI_REQUEST_FAILED",
                "AI 서비스 요청이 시간 초과되었거나 정상적으로 완료되지 않았습니다.");
    }

    public static ChecklistSupplementResult invalidResponse() {
        return unavailable(
                "AI_RESPONSE_INVALID",
                "AI 조사 응답을 검증하지 못해 결과를 적용하지 않았습니다.");
    }

    private static ChecklistSupplementResult unavailable(String code, String message) {
        return new ChecklistSupplementResult(false, List.of(), List.of(), code, message);
    }
}
