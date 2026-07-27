package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.product.dto.response.ChecklistTemplateResponse;
import com.c203.limit.domain.product.dto.response.DeviceCategoryResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelDetailResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelSummaryResponse;
import com.c203.limit.domain.product.dto.response.HandoverGuideResponse;
import com.c203.limit.domain.product.service.ProductCatalogService;
import com.c203.limit.domain.product.service.ProductCatalogService.ModelPage;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.response.PageMetaResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductCatalogController implements ProductCatalogApi {
    private final ProductCatalogService catalogService;

    public ProductCatalogController(ProductCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Override
    public ResponseEntity<ApiResponse<List<DeviceCategoryResponse>>> getDeviceCategories(
            Long parentId, boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.categories(parentId, activeOnly)));
    }

    @Override
    public ResponseEntity<ApiResponse<List<DeviceModelSummaryResponse>>> getDeviceModels(
            Long categoryId,
            Long manufacturerId,
            String keyword,
            int page,
            int size) {
        ModelPage result =
                catalogService.models(categoryId, manufacturerId, keyword, page, size);
        return ResponseEntity.ok(new ApiResponse<>(
                result.content(),
                new PageMetaResponse(
                        result.page(),
                        result.size(),
                        result.totalElements(),
                        result.totalPages(),
                        result.hasNext())));
    }

    @Override
    public ResponseEntity<ApiResponse<DeviceModelDetailResponse>> getDeviceModel(
            Long deviceModelId) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.model(deviceModelId)));
    }

    @Override
    public ResponseEntity<ApiResponse<ChecklistTemplateResponse>> getChecklistTemplate(
            Long deviceModelId) {
        return ResponseEntity.ok(
                ApiResponse.ok(catalogService.checklistTemplate(deviceModelId)));
    }

    @Override
    public ResponseEntity<ApiResponse<HandoverGuideResponse>> getHandoverGuide(
            Long deviceModelId) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.handoverGuide(deviceModelId)));
    }
}
