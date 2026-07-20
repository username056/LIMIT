package com.c203.limit.withdrawal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class WithdrawalController implements WithdrawalApi {

    @Override
    public ResponseEntity<Void> withdrawal01(Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> withdrawal02() {
        return null;
    }

    @Override
    public ResponseEntity<Void> withdrawal03(Long withdrawalRequestId) {
        return null;
    }
}
