package com.c203.limit.domain.product.entity;

/** 매물 라이프사이클 상태. DDL: listing.status */
public enum ListingStatus {
    DRAFT,
    ON_SALE,
    RESERVED,
    PAID,
    INSPECTING,
    CONFIRMED,
    SETTLED,
    CANCELLED,
    HIDDEN,
    SUSPENDED
}
