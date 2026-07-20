package com.c203.limit.domain.queue.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class QueueController implements QueueApi {

    @Override
    public ResponseEntity<Void> queue01(Long saleId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> queue02(Long saleId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> queue03(Long saleId, String idempotencyKey, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> queue04(Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> queue05(Object body) {
        return null;
    }
}
