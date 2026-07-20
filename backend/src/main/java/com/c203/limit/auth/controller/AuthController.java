package com.c203.limit.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class AuthController implements AuthApi {

    @Override
    public ResponseEntity<Void> auth01(String email) {
        return null;
    }

    @Override
    public ResponseEntity<Void> auth02(String nickname) {
        return null;
    }

    @Override
    public ResponseEntity<Void> auth03(Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> auth04(Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> auth05(Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> auth06(Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> auth07(String provider, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> auth08() {
        return null;
    }

    @Override
    public ResponseEntity<Void> auth09(Long socialAccountId) {
        return null;
    }
}
