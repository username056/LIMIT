package com.c203.limit.domain.payment.client;

record TossConfirmRequest(String paymentKey, String orderId, long amount) {}
