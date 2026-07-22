package com.c203.limit.domain.refund.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class RefundController implements RefundApi {

    @Override
    public ResponseEntity<Void> refund01(Long paymentId, String idempotencyKey, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> refund02(Long refundId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> refund03(Long paymentId) {
        return null;
    }
}
