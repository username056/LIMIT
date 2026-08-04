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
        String sourceTitle,
        @Schema(
                        description = "선택 시 생성되는 항목의 증빙 유형",
                        allowableValues = {"PHOTO", "VIDEO", "DIAGNOSTIC_FILE", "SELLER_CONFIRMATION"})
                String evidenceType,
        @Schema(description = "이 기능을 선택하면 생성되는 체크리스트 항목 코드. 상품 수정 시 기존 항목과 대조해 선택 상태를 복원하는 데 사용한다.")
                String itemCode) {

    public static ChecklistSuggestionResponse from(ChecklistSuggestion suggestion) {
        return new ChecklistSuggestionResponse(
                suggestion.featureCode(),
                suggestion.featureName(),
                suggestion.evidenceStatus().name(),
                suggestion.reason(),
                suggestion.checkGuide(),
                suggestion.sourceUrl(),
                suggestion.sourceTitle(),
                suggestion.evidenceType().name(),
                suggestion.itemCode());
    }
}
