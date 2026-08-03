package com.c203.limit.domain.admin.dto.response;

import java.time.LocalDateTime;

public record AdminRelatedProductResponse(
        Long productId,
        Long sellerId,
        String title,
        long price,
        String status,
        Long checklistTemplateId,
        boolean precheckCompleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
