package com.c203.limit.domain.product.dto.response;

import com.c203.limit.domain.inspection.checklist.GeneratedChecklist;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "ChecklistGenerationResponse", description = "기기 모델 체크리스트 생성 결과")
public record ChecklistGenerationResponse(
        Long deviceModelId,
        String manufacturer,
        String modelName,
        String osFamily,
        int templateVersion,
        boolean aiApplied,
        List<GeneratedChecklistItemResponse> items,
        List<ChecklistSuggestionResponse> aiSuggestions,
        List<String> reviewCandidates,
        Long researchId,
        String researchStatus) {

    public static ChecklistGenerationResponse from(GeneratedChecklist checklist) {
        return new ChecklistGenerationResponse(
                checklist.deviceModelId(),
                checklist.manufacturer(),
                checklist.modelName(),
                checklist.osFamily().name(),
                checklist.templateVersion(),
                checklist.aiApplied(),
                checklist.items().stream()
                        .map(GeneratedChecklistItemResponse::from)
                        .toList(),
                checklist.aiSuggestions().stream()
                        .map(ChecklistSuggestionResponse::from)
                        .toList(),
                checklist.reviewCandidates(),
                checklist.researchId(),
                checklist.researchStatus());
    }
}
