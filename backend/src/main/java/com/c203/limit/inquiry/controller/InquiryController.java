package com.c203.limit.inquiry.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class InquiryController implements InquiryApi {

    @Override
    public ResponseEntity<Void> inquiry01(Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> inquiry02(Integer page, Integer size, String status, String category) {
        return null;
    }

    @Override
    public ResponseEntity<Void> inquiry03(Long inquiryId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> inquiry04(Long inquiryId, Object body) {
        return null;
    }
}
