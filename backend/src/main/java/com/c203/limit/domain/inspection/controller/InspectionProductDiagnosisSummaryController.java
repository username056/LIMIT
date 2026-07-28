package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.response.ProductDiagnosisSummaryResponse;
import com.c203.limit.domain.inspection.service.ProductDiagnosisSummaryService;
import com.c203.limit.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InspectionProductDiagnosisSummaryController implements InspectionProductDiagnosisSummaryApi {

    private final ProductDiagnosisSummaryService productDiagnosisSummaryService;

    public InspectionProductDiagnosisSummaryController(ProductDiagnosisSummaryService productDiagnosisSummaryService) {
        this.productDiagnosisSummaryService = productDiagnosisSummaryService;
    }

    @Override
    public ResponseEntity<ApiResponse<ProductDiagnosisSummaryResponse>> getProductDiagnosisSummary(Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(productDiagnosisSummaryService.getSummary(productId)));
    }
}
