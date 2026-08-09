package com.c203.limit.domain.product.moderation.entity;

/** 거래 라이프사이클과 분리된 매물 운영 상태. */
public enum ListingModerationStatus {
    NORMAL,
    WARNING_ACK_REQUIRED,
    SUSPENDED,
    RESTORE_REQUESTED
}
