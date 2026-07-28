package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.response.DiagnosisFieldListResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "09. 검수", description = "검수 증거 OCR·DxDiag·배터리 리포트 파싱 API")
public interface InspectionDiagnosisApi {

    @Operation(
            operationId = "diagnosis01",
            summary = "체크리스트 항목 진단값 취합 조회",
            description =
                    "체크리스트 항목에 딸린 증거들의 OCR 결과와 진단 파일(dxdiag/배터리 리포트) 파싱 결과를 같은 필드 기준으로"
                            + " 취합해 값이 서로 다른 상충 여부와 함께 반환합니다. 해당 항목이 속한 매물의 판매자 본인만 호출할 수 있습니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "취합 결과 조회 성공",
                content = @Content(schema = @Schema(implementation = DiagnosisFieldListResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "FORBIDDEN"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "ITEM_NOT_FOUND")
    })
    @GetMapping(
            path = "/api/v1/inspections/listing-checklist-items/{itemId}/diagnosis",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<DiagnosisFieldListResponse>> getDiagnosis(@PathVariable Long itemId);
}
