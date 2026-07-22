package com.c203.limit.domain.favorite.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class FavoriteController implements FavoriteApi {

    @Override
    public ResponseEntity<Void> favorite01(Integer page, Integer size) {
        return null;
    }

    @Override
    public ResponseEntity<Void> favorite02(Long productId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> favorite03(Long productId) {
        return null;
    }
}
