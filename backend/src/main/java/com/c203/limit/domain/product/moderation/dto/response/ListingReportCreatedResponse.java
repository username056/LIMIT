package com.c203.limit.domain.product.moderation.dto.response;

import java.time.LocalDateTime;

public record ListingReportCreatedResponse(
        Long reportId,
        Long productId,
        String category,
        String status,
        LocalDateTime createdAt) {}
