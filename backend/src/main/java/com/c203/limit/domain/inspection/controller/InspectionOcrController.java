package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.response.OcrResultResponse;
import com.c203.limit.domain.inspection.service.OcrExtractionService;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InspectionOcrController implements InspectionOcrApi {

    private final OcrExtractionService ocrExtractionService;
    private final CurrentUser currentUser;

    public InspectionOcrController(OcrExtractionService ocrExtractionService, CurrentUser currentUser) {
        this.ocrExtractionService = ocrExtractionService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<ApiResponse<OcrResultResponse>> extractOcrText(Long evidenceId) {
        OcrResultResponse response =
                ocrExtractionService.extractAndStructure(evidenceId, currentUser.memberId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }
}
