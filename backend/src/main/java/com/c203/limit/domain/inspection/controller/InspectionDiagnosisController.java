package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.response.DiagnosisFieldListResponse;
import com.c203.limit.domain.inspection.service.DiagnosisAggregationService;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InspectionDiagnosisController implements InspectionDiagnosisApi {

    private final DiagnosisAggregationService diagnosisAggregationService;
    private final CurrentUser currentUser;

    public InspectionDiagnosisController(
            DiagnosisAggregationService diagnosisAggregationService, CurrentUser currentUser) {
        this.diagnosisAggregationService = diagnosisAggregationService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<ApiResponse<DiagnosisFieldListResponse>> getDiagnosis(Long itemId) {
        DiagnosisFieldListResponse response =
                diagnosisAggregationService.getDiagnosis(itemId, currentUser.memberId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
