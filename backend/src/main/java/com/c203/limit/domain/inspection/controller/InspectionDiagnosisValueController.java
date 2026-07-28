package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.request.DiagnosisValueUpdateRequest;
import com.c203.limit.domain.inspection.dto.response.DiagnosisValueUpdateResponse;
import com.c203.limit.domain.inspection.service.DiagnosisValueConfirmationService;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InspectionDiagnosisValueController implements InspectionDiagnosisValueApi {

    private final DiagnosisValueConfirmationService diagnosisValueConfirmationService;
    private final CurrentUser currentUser;

    public InspectionDiagnosisValueController(
            DiagnosisValueConfirmationService diagnosisValueConfirmationService, CurrentUser currentUser) {
        this.diagnosisValueConfirmationService = diagnosisValueConfirmationService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<ApiResponse<DiagnosisValueUpdateResponse>> confirmDiagnosisValue(
            Long itemId, DiagnosisValueUpdateRequest request) {
        DiagnosisValueUpdateResponse response =
                diagnosisValueConfirmationService.confirm(itemId, currentUser.memberId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
