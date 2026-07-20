package com.c203.limit.seller.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class SellerController implements SellerApi {

    @Override
    public ResponseEntity<Void> sellerapp01(Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> sellerapp02(Integer page, Integer size) {
        return null;
    }

    @Override
    public ResponseEntity<Void> sellerapp03(Long applicationId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> sellerapp04(Long applicationId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> sellerapp05(Long applicationId, MultipartFile[] files) {
        return null;
    }

    @Override
    public ResponseEntity<Void> sellerapp06(Long applicationId, Long documentId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> sellerapp07(Long applicationId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> sellerapp08(Long applicationId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> seller01() {
        return null;
    }
}
