package com.c203.limit.domain.product.moderation.dto.response;

import java.time.LocalDateTime;

public record AdminModeratedProductResponse(
        Long productId,
        String productName,
        Long sellerId,
        String sellerNickname,
        String lifecycleStatus,
        String moderationStatus,
        long pendingReportCount,
        long openRiskSignalCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
