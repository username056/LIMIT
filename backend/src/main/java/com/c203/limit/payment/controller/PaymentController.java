package com.c203.limit.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class PaymentController implements PaymentApi {

    @Override
    public ResponseEntity<Void> pay01(String idempotencyKey, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> pay02(Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> pay03(Long paymentId, Object body) {
        return null;
    }
}
