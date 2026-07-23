package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.request.ExtractOcrTextRequest;
import com.c203.limit.domain.inspection.dto.response.OcrResultResponse;
import com.c203.limit.domain.inspection.entity.OcrResult;
import com.c203.limit.domain.inspection.service.OcrExtractionService;
import com.c203.limit.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InspectionOcrController implements InspectionOcrApi {

    private final OcrExtractionService ocrExtractionService;

    public InspectionOcrController(OcrExtractionService ocrExtractionService) {
        this.ocrExtractionService = ocrExtractionService;
    }

    @Override
    public ResponseEntity<ApiResponse<OcrResultResponse>> extractOcrText(
            Long evidenceId, ExtractOcrTextRequest request) {
        OcrResult ocrResult = ocrExtractionService.extractText(evidenceId, request.getFieldType());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(OcrResultResponse.from(ocrResult)));
    }
}
