package com.c203.limit.domain.product.moderation.dto.response;

import java.time.LocalDateTime;

public record RestorationRequestResponse(
        Long restorationRequestId,
        Long productId,
        Long sellerId,
        String requestNote,
        String status,
        Long reviewerAdminId,
        String reviewNote,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt) {}
