package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.response.DxdiagResultResponse;
import com.c203.limit.domain.inspection.service.DxdiagParsingService;
import com.c203.limit.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InspectionDxdiagController implements InspectionDxdiagApi {

    private final DxdiagParsingService dxdiagParsingService;

    public InspectionDxdiagController(DxdiagParsingService dxdiagParsingService) {
        this.dxdiagParsingService = dxdiagParsingService;
    }

    @Override
    public ResponseEntity<ApiResponse<DxdiagResultResponse>> parseDxdiag(Long evidenceId) {
        DxdiagParsingService.DxdiagParsingResult result = dxdiagParsingService.parse(evidenceId);
        DxdiagResultResponse response = DxdiagResultResponse.from(result.entity(), result.parsed());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }
}
