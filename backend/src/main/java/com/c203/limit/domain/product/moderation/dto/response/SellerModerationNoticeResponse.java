package com.c203.limit.domain.product.moderation.dto.response;

import java.time.LocalDateTime;

/** 판매자에게만 노출하는 신고 안내. 신고자 식별 정보는 포함하지 않는다. */
public record SellerModerationNoticeResponse(
        Long reportId,
        String category,
        String detail,
        String adminNote,
        LocalDateTime reviewedAt) {}
