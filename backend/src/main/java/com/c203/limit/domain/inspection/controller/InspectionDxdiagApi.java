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
            summary = "DxDiag 진단 파일 파싱",
            description =
                    "업로드 완료된 DxDiag 진단 파일(txt/xml)을 포맷에 맞춰 파싱해 CPU·메모리·GPU·드라이버·사운드 장치 정보를 추출하고"
                            + " 결과를 저장합니다. 해당 증거가 속한 매물의 판매자 본인만 호출할 수 있습니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "DxDiag 파싱 완료 (status로 성공·부분·실패를 구분, missingFields로 누락 필드 확인)",
                content = @Content(schema = @Schema(implementation = DxdiagResultResponse.class))),
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
            path = "/api/v1/inspections/evidence/{evidenceId}/dxdiag-results",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<DxdiagResultResponse>> parseDxdiag(@PathVariable Long evidenceId);
}
