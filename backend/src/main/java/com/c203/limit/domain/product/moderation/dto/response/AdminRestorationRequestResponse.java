package com.c203.limit.domain.product.moderation.dto.response;

import java.time.LocalDateTime;

public record AdminRestorationRequestResponse(
        Long restorationRequestId,
        Long productId,
        String productName,
        Long sellerId,
        String requestNote,
        String status,
        String moderationStatus,
        Long reviewerAdminId,
        String reviewNote,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime productUpdatedAt) {}
