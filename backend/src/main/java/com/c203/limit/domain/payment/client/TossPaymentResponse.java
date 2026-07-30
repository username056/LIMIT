package com.c203.limit.domain.payment.client;

import java.time.OffsetDateTime;

public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        long totalAmount,
        String method,
        OffsetDateTime approvedAt) {}
