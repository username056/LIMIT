package com.c203.limit.domain.product.moderation.dto.response;

import java.util.List;

public record ModerationDashboardResponse(
        long pendingReportCount,
        long warningRequiredProductCount,
        long suspendedProductCount,
        long pendingRestorationCount,
        long openRiskSignalCount,
        List<SuspiciousSellerResponse> suspiciousSellers,
        List<ModerationRiskSignalResponse> recentRiskSignals) {}
