package com.c203.limit.domain.inspection.reinspection.controller;

import com.c203.limit.domain.inspection.reinspection.dto.request.ReinspectionRequestCreateRequest;
import com.c203.limit.domain.inspection.reinspection.dto.response.ReinspectionRequestResponse;
import com.c203.limit.domain.inspection.reinspection.service.ReinspectionRequestService;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReinspectionRequestController implements ReinspectionRequestApi {

    private final ReinspectionRequestService reinspectionRequestService;
    private final CurrentUser currentUser;

    public ReinspectionRequestController(
            ReinspectionRequestService reinspectionRequestService, CurrentUser currentUser) {
        this.reinspectionRequestService = reinspectionRequestService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<ApiResponse<ReinspectionRequestResponse>> request(
            Long listingId, ReinspectionRequestCreateRequest request) {
        ReinspectionRequestResponse response =
                reinspectionRequestService.request(listingId, currentUser.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Override
    public ResponseEntity<ApiResponse<ReinspectionRequestResponse>> complete(String requestKey) {
        ReinspectionRequestResponse response =
                reinspectionRequestService.complete(requestKey, currentUser.memberId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
