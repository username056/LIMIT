package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.request.DiagnosisValueUpdateRequest;
import com.c203.limit.domain.inspection.dto.response.DiagnosisValueUpdateResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "06. 검수", description = "검수 증거 OCR·DxDiag·배터리 리포트 파싱 API")
public interface InspectionDiagnosisValueApi {

    @Operation(
            operationId = "diagnosisValue01",
            summary = "체크리스트 항목 진단값 확정/수정",
            description =
                    "체크리스트 항목의 필드 하나를 판매자가 확정값으로 저장합니다. 이 필드를 처음 수정하는 경우"
                            + " OCR/진단파일에서 취합된 당시 원본값을 이력에 먼저 자동 기록한 뒤 확정값을 저장하고,"
                            + " 이후에는 수정 이력만 계속 쌓입니다. 해당 항목이 속한 매물의 판매자 본인만 호출할 수 있습니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "확정/수정 완료",
                content = @Content(schema = @Schema(implementation = DiagnosisValueUpdateResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400", description = "FIELD_NOT_EDITABLE"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "FORBIDDEN"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "ITEM_NOT_FOUND")
    })
    @PatchMapping(
            path = "/api/v1/inspections/listing-checklist-items/{itemId}/diagnosis-values",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<DiagnosisValueUpdateResponse>> confirmDiagnosisValue(
            @PathVariable Long itemId, @Valid @RequestBody DiagnosisValueUpdateRequest request);
}
