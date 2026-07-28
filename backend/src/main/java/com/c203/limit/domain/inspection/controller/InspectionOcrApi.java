package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.response.OcrResultResponse;
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

@Tag(name = "09. 검수", description = "검수 증거 OCR·DxDiag·배터리 리포트 파싱 API")
public interface InspectionOcrApi {

    @Operation(
            operationId = "ocr01",
            summary = "증거 스크린샷 OCR 자동 구조화",
            description =
                    "업로드 완료된 증거 스크린샷(설정 > 시스템 > 정보 화면)을 OCR로 분석해 기대 필드(MODEL_NAME, CPU,"
                            + " RAM, GPU, STORAGE_CAPACITY, OS_VERSION)를 감지된 만큼 구조화해 저장합니다. 해당 증거가"
                            + " 속한 매물의 판매자 본인만 호출할 수 있습니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "OCR 자동 구조화 완료 (status: SUCCESS|PARTIAL|FAILED)",
                content = @Content(schema = @Schema(implementation = OcrResultResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400", description = "INVALID_EVIDENCE_TYPE"),
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
            path = "/api/v1/inspections/evidence/{evidenceId}/ocr-results",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<OcrResultResponse>> extractOcrText(@PathVariable Long evidenceId);
}
