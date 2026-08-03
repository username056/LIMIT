package com.c203.limit.domain.admin.dto.response;

import com.c203.limit.domain.product.dto.response.ChecklistTemplateItemResponse;
import java.time.LocalDateTime;
import java.util.List;

public record AdminDeviceModelDetailResponse(
        Long modelId,
        Long categoryId,
        String categoryName,
        String manufacturer,
        String modelName,
        String modelCode,
        String osFamily,
        String reviewStatus,
        String sourceType,
        boolean isActive,
        Long reportedByMemberId,
        Long reviewedByAdminId,
        LocalDateTime reviewedAt,
        String reviewNote,
        LocalDateTime disabledAt,
        Long disabledByAdminId,
        String disableReason,
        Long replacementModelId,
        List<ChecklistTemplateItemResponse> baseChecklistItems,
        ChecklistResearchResponse latestResearch,
        AdminDeviceModelImpactResponse impact,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
