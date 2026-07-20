package com.c203.limit.cart.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class CartController implements CartApi {

    @Override
    public ResponseEntity<Void> cart01() {
        return null;
    }

    @Override
    public ResponseEntity<Void> cart02(Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> cart03(Long cartItemId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> cart04(Long cartItemId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> cart05() {
        return null;
    }
}
