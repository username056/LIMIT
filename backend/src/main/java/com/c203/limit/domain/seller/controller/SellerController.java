package com.c203.limit.domain.seller.controller;

import com.c203.limit.domain.seller.dto.request.CreateSellerRequest;
import com.c203.limit.domain.seller.dto.response.SellerProfileResponse;
import com.c203.limit.domain.seller.service.SellerService;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SellerController implements SellerApi {
    private final SellerService sellerService;
    private final CurrentUser currentUser;

    public SellerController(SellerService sellerService, CurrentUser currentUser) {
        this.sellerService = sellerService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<ApiResponse<SellerProfileResponse>> register(
            CreateSellerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(sellerService.register(currentUser.memberId(), request)));
    }

    @Override
    public ResponseEntity<ApiResponse<SellerProfileResponse>> profile() {
        return ResponseEntity.ok(ApiResponse.ok(sellerService.profile(currentUser.memberId())));
    }
}
