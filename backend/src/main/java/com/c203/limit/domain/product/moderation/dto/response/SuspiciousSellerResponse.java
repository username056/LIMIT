package com.c203.limit.domain.product.moderation.dto.response;

public record SuspiciousSellerResponse(
        Long sellerId,
        String nickname,
        long publishedLastSevenDays,
        long openRiskSignalCount,
        String riskLevel) {}
