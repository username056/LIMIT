package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.response.BatteryReportResultResponse;
import com.c203.limit.domain.inspection.entity.BatteryReportResult;
import com.c203.limit.domain.inspection.service.BatteryReportParsingService;
import com.c203.limit.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InspectionBatteryReportController implements InspectionBatteryReportApi {

    private final BatteryReportParsingService batteryReportParsingService;

    public InspectionBatteryReportController(BatteryReportParsingService batteryReportParsingService) {
        this.batteryReportParsingService = batteryReportParsingService;
    }

    @Override
    public ResponseEntity<ApiResponse<BatteryReportResultResponse>> parseBatteryReport(Long evidenceId) {
        BatteryReportResult result = batteryReportParsingService.parse(evidenceId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(BatteryReportResultResponse.from(result)));
    }
}
