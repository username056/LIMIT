package com.c203.limit.stock.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class StockController implements StockApi {

    @Override
    public ResponseEntity<Void> stock01(Long saleId, String idempotencyKey, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> stock02(Long reservationId, String idempotencyKey, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> stock03(Long reservationId, String idempotencyKey, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> stock04(Long saleId, String idempotencyKey, Object body) {
        return null;
    }
}
