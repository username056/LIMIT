package com.c203.limit.domain.product.dto.response;

import com.c203.limit.domain.inspection.checklist.ChecklistSuggestion;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "ChecklistSuggestionResponse",
        description = "공식 자료에서 확인한 추가 기능 후보. 판매자 확인 후 체크리스트에 반영됩니다.")
public record ChecklistSuggestionResponse(
        String featureCode,
        String featureName,
        String evidenceStatus,
        String reason,
        String checkGuide,
        String sourceUrl,
        String sourceTitle) {

    public static ChecklistSuggestionResponse from(ChecklistSuggestion suggestion) {
        return new ChecklistSuggestionResponse(
                suggestion.featureCode(),
                suggestion.featureName(),
                suggestion.evidenceStatus().name(),
                suggestion.reason(),
                suggestion.checkGuide(),
                suggestion.sourceUrl(),
                suggestion.sourceTitle());
    }
}
