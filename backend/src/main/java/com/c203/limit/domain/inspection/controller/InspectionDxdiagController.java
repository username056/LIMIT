package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.response.DxdiagResultResponse;
import com.c203.limit.domain.inspection.entity.DxdiagResult;
import com.c203.limit.domain.inspection.mapper.DxdiagResultMapper;
import com.c203.limit.domain.inspection.service.DxdiagParsingService;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InspectionDxdiagController implements InspectionDxdiagApi {

    private final DxdiagParsingService dxdiagParsingService;
    private final CurrentUser currentUser;

    public InspectionDxdiagController(DxdiagParsingService dxdiagParsingService, CurrentUser currentUser) {
        this.dxdiagParsingService = dxdiagParsingService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<ApiResponse<DxdiagResultResponse>> parseDxdiag(Long evidenceId) {
        DxdiagResult result = dxdiagParsingService.parse(evidenceId, currentUser.memberId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(DxdiagResultMapper.toResponse(result)));
    }
}
