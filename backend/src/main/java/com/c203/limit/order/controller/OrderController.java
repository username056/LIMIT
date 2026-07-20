package com.c203.limit.order.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class OrderController implements OrderApi {

    @Override
    public ResponseEntity<Void> order01(String idempotencyKey, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> order02(Integer page, Integer size, String status, String role) {
        return null;
    }

    @Override
    public ResponseEntity<Void> order021(Long orderId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> order03(Long orderId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> order04(Long orderId) {
        return null;
    }
}
