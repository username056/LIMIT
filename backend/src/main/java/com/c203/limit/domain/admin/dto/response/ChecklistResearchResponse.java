package com.c203.limit.domain.admin.dto.response;

import com.c203.limit.domain.product.dto.response.ChecklistSuggestionResponse;
import java.time.LocalDateTime;
import java.util.List;

public record ChecklistResearchResponse(
        Long researchId,
        Long deviceModelId,
        String deviceType,
        String manufacturer,
        String modelName,
        int researchVersion,
        String status,
        List<ChecklistSuggestionResponse> suggestions,
        List<String> reviewCandidates,
        Long publishedTemplateId,
        Long reviewedByAdminId,
        String reviewNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
