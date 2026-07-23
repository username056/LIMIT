package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.request.ExtractOcrTextRequest;
import com.c203.limit.domain.inspection.dto.response.OcrResultResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "06. 검수", description = "검수 증거 OCR·DxDiag·배터리 리포트 파싱 API")
public interface InspectionOcrApi {

    @Operation(
            operationId = "ocr01",
            summary = "증거 이미지 OCR 텍스트 추출",
            description = "업로드 완료된 증거 이미지를 네이버 클로바 OCR로 분석해 텍스트를 추출하고 결과를 저장합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "OCR 추출 성공",
                content = @Content(schema = @Schema(implementation = OcrResultResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "EVIDENCE_NOT_FOUND"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409", description = "EVIDENCE_NOT_READY"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422", description = "OCR_RECOGNITION_FAILED")
    })
    @PostMapping(
            path = "/api/v1/inspections/evidence/{evidenceId}/ocr-results",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<OcrResultResponse>> extractOcrText(
            @PathVariable Long evidenceId, @Valid @RequestBody ExtractOcrTextRequest request);
}
