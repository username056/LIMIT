package com.c203.limit.domain.payment.entity;

public enum PaymentStatus {
    REQUESTED,
    APPROVED,
    FAILED,
    CANCELLED,
    EXPIRED,
    REFUND_REQUESTED,
    REFUND_PENDING,
    REFUNDED
}
