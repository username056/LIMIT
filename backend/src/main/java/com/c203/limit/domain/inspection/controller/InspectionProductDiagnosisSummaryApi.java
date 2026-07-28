package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.response.ProductDiagnosisSummaryResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "06. 검수", description = "검수 증거 OCR·DxDiag·배터리 리포트 파싱 API")
public interface InspectionProductDiagnosisSummaryApi {

    @Operation(
            operationId = "productDiagnosisSummary01",
            summary = "상품 진단 최종 요약 조회",
            description =
                    "구매자가 상품 상세에서 보는 검수 진단 최종 요약입니다. 매물의 체크리스트 항목마다 필드별 자동 추출값과"
                            + " 판매자 확정값을 함께 보여주며, 인증 없이 누구나 조회할 수 있습니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = @Content(schema = @Schema(implementation = ProductDiagnosisSummaryResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "PRODUCT_NOT_FOUND")
    })
    @GetMapping(
            path = "/api/v1/inspections/products/{productId}/diagnosis-summary",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<ProductDiagnosisSummaryResponse>> getProductDiagnosisSummary(
            @PathVariable Long productId);
}
