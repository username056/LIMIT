package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.response.BatteryReportResultResponse;
import com.c203.limit.domain.inspection.entity.BatteryReportResult;
import com.c203.limit.domain.inspection.mapper.BatteryReportResultMapper;
import com.c203.limit.domain.inspection.service.BatteryReportParsingService;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InspectionBatteryReportController implements InspectionBatteryReportApi {

    private final BatteryReportParsingService batteryReportParsingService;
    private final CurrentUser currentUser;

    public InspectionBatteryReportController(
            BatteryReportParsingService batteryReportParsingService, CurrentUser currentUser) {
        this.batteryReportParsingService = batteryReportParsingService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<ApiResponse<BatteryReportResultResponse>> parseBatteryReport(Long evidenceId) {
        BatteryReportResult result = batteryReportParsingService.parse(evidenceId, currentUser.memberId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(BatteryReportResultMapper.toResponse(result)));
    }
}
