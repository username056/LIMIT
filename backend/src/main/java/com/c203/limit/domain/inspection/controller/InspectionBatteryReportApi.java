package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.response.BatteryReportResultResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Tag(name = "06. 검수", description = "검수 증거 OCR·DxDiag·배터리 리포트 파싱 API")
public interface InspectionBatteryReportApi {

    @Operation(
            operationId = "batteryReport01",
            summary = "배터리 리포트 HTML 파싱",
            description =
                    "업로드 완료된 powercfg 배터리 리포트 HTML 증거를 파싱해 제조사·설계 용량·완전충전 용량·사이클 수를 추출하고 결과를 저장합니다."
                            + " 해당 증거가 속한 매물의 판매자 본인만 호출할 수 있습니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "배터리 리포트 파싱 완료 (status로 성공·부분·실패를 구분)",
                content = @Content(schema = @Schema(implementation = BatteryReportResultResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400", description = "UNSUPPORTED_FILE_FORMAT"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "FORBIDDEN"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "EVIDENCE_NOT_FOUND"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409", description = "EVIDENCE_NOT_READY"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422", description = "PARSING_FAILED")
    })
    @PostMapping(
            path = "/api/v1/inspections/evidence/{evidenceId}/battery-report-results",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<BatteryReportResultResponse>> parseBatteryReport(
            @PathVariable Long evidenceId);
}
