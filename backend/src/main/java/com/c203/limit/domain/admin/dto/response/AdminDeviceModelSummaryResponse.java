package com.c203.limit.domain.admin.dto.response;

import java.time.LocalDateTime;

public record AdminDeviceModelSummaryResponse(
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
        String latestResearchStatus,
        Integer latestResearchVersion,
        long relatedProductCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
