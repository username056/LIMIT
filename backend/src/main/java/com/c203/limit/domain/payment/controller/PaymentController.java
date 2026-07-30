package com.c203.limit.domain.payment.controller;

import com.c203.limit.domain.payment.dto.request.ConfirmPaymentRequest;
import com.c203.limit.domain.payment.dto.request.CreatePaymentRequest;
import com.c203.limit.domain.payment.dto.response.PaymentResponse;
import com.c203.limit.domain.payment.service.PaymentService;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController implements PaymentApi {
    private final PaymentService paymentService;
    private final CurrentUser currentUser;

    public PaymentController(PaymentService paymentService, CurrentUser currentUser) {
        this.paymentService = paymentService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(CreatePaymentRequest request) {
        PaymentResponse response = paymentService.request(currentUser.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Override
    public ResponseEntity<ApiResponse<PaymentResponse>> confirmPayment(
            Long paymentId, ConfirmPaymentRequest request) {
        PaymentResponse response = paymentService.confirm(currentUser.memberId(), paymentId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Override
    public ResponseEntity<ApiResponse<PaymentResponse>> cancelPayment(Long paymentId) {
        PaymentResponse response = paymentService.cancel(currentUser.memberId(), paymentId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Override
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(Long paymentId) {
        return ResponseEntity.ok(
                ApiResponse.ok(paymentService.get(currentUser.memberId(), paymentId)));
    }
}
