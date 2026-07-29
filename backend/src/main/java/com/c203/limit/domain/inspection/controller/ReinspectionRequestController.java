package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.request.ReinspectionRequestCreateRequest;
import com.c203.limit.domain.inspection.dto.response.ReinspectionRequestResponse;
import com.c203.limit.domain.inspection.service.ReinspectionRequestService;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReinspectionRequestController implements ReinspectionRequestApi {
    private final ReinspectionRequestService service;
    private final CurrentUser currentUser;

    public ReinspectionRequestController(
            ReinspectionRequestService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<ApiResponse<ReinspectionRequestResponse>> create(
            Long listingId, ReinspectionRequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(
                        listingId, currentUser.memberId(), request)));
    }

    @Override
    public ResponseEntity<ApiResponse<ReinspectionRequestResponse>> complete(
            String requestKey) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.complete(requestKey, currentUser.memberId())));
    }
}
