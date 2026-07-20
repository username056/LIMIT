package com.c203.limit.product.controller;

import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class ProductController implements ProductApi {

    @Override
    public ResponseEntity<Void> product1(Object body, MultipartFile[] files) {
        return null;
    }

    @Override
    public ResponseEntity<Void> product2(Long productId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> product3(String keyword, Long brandId, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, String saleStatus, Integer page, Integer size, String sort) {
        return null;
    }

    @Override
    public ResponseEntity<Void> product4(Long productId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> product5(Long productId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> product6(String name, Long brandId, Long categoryId, String saleStatus, Boolean hasReport, Integer page, Integer size, String sort) {
        return null;
    }

    @Override
    public ResponseEntity<Void> product7(String name, Long sellerId, Long brandId, Long categoryId, String saleStatus, Boolean hasReport, Integer page, Integer size, String sort) {
        return null;
    }

    @Override
    public ResponseEntity<Void> product8(Long productId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> product10(Long productId, Integer page, Integer size) {
        return null;
    }
}
