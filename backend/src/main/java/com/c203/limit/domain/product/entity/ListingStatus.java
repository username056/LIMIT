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
    SUSPENDED,
    /**
     * 판매자가 직접 판매 완료로 종료한 상태. 서비스 결제를 거치지 않은 직거래를 정리하기 위한
     * 출구이며, 결제 흐름(RESERVED→PAID→…→SETTLED)과는 별개 경로다.
     */
    SOLD
}
