package com.c203.limit.address.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AddressController implements AddressApi {

    @Override
    public ResponseEntity<Void> address01(Boolean includeDeleted) {
        return null;
    }

    @Override
    public ResponseEntity<Void> address02(Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> address03(Long addressId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> address04(Long addressId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> address05(Long addressId) {
        return null;
    }
}
