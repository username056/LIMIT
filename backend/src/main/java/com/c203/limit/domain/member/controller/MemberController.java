package com.c203.limit.domain.member.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class MemberController implements MemberApi {

    @Override
    public ResponseEntity<Void> member01() {
        return null;
    }

    @Override
    public ResponseEntity<Void> member02(Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> member03(Object body) {
        return null;
    }
}
