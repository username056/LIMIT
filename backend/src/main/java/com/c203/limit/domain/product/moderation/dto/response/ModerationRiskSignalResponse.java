package com.c203.limit.domain.product.moderation.dto.response;

import java.time.LocalDateTime;

public record ModerationRiskSignalResponse(
        Long riskSignalId,
        Long sellerId,
        Long productId,
        Long relatedProductId,
        String signalType,
        int score,
        String detail,
        String status,
        String resolutionNote,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt) {}
