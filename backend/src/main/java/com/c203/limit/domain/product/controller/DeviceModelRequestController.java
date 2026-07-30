package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.product.dto.request.CreateDeviceModelRequest;
import com.c203.limit.domain.product.dto.response.DeviceModelRequestResponse;
import com.c203.limit.domain.product.service.DeviceModelRequestService;
import com.c203.limit.domain.seller.service.SellerStatusReader;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeviceModelRequestController implements DeviceModelRequestApi {
    private final DeviceModelRequestService requestService;
    private final CurrentUser currentUser;
    private final SellerStatusReader sellerStatusReader;

    public DeviceModelRequestController(
            DeviceModelRequestService requestService,
            CurrentUser currentUser,
            SellerStatusReader sellerStatusReader) {
        this.requestService = requestService;
        this.currentUser = currentUser;
        this.sellerStatusReader = sellerStatusReader;
    }

    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<DeviceModelRequestResponse>> createDeviceModelRequest(
            CreateDeviceModelRequest request) {
        Long memberId = currentUser.memberId();
        sellerStatusReader.requireActiveSeller(memberId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(requestService.create(memberId, request)));
    }
}
