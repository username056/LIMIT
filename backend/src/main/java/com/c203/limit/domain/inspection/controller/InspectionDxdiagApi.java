package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.response.DxdiagResultResponse;
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
public interface InspectionDxdiagApi {

    @Operation(
            operationId = "dxdiag01",
            summary = "DxDiag.xml 파싱",
            description = "업로드 완료된 DxDiag.xml 증거를 DOM으로 파싱해 제조사·모델·OS·CPU·메모리·GPU·드라이버 정보를 추출하고 결과를 저장합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "DxDiag 파싱 완료 (parseStatus로 성공·부분·실패를 구분)",
                content = @Content(schema = @Schema(implementation = DxdiagResultResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "EVIDENCE_NOT_FOUND"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409", description = "EVIDENCE_NOT_READY"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400", description = "INVALID_EVIDENCE_TYPE")
    })
    @PostMapping(
            path = "/api/v1/inspections/evidence/{evidenceId}/dxdiag-results",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<DxdiagResultResponse>> parseDxdiag(@PathVariable Long evidenceId);
}
