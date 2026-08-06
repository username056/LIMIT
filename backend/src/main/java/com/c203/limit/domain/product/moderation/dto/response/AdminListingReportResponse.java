package com.c203.limit.domain.product.moderation.dto.response;

import java.time.LocalDateTime;

public record AdminListingReportResponse(
        Long reportId,
        Long productId,
        String productName,
        Long sellerId,
        Long reporterId,
        String category,
        String detail,
        String status,
        String lifecycleStatus,
        String moderationStatus,
        Long reviewerAdminId,
        String adminNote,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt) {}
