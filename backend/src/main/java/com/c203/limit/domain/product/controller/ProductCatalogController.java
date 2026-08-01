package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.product.dto.response.ChecklistTemplateResponse;
import com.c203.limit.domain.product.dto.response.DeviceCategoryResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelDetailResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelOptionsResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelSummaryResponse;
import com.c203.limit.domain.product.dto.response.HandoverGuideResponse;
import com.c203.limit.domain.product.entity.DeviceVariantAxis;
import com.c203.limit.domain.product.service.DeviceCatalogOptionService;
import com.c203.limit.domain.product.service.ProductCatalogService;
import com.c203.limit.domain.product.service.ProductCatalogService.ModelPage;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.response.PageMetaResponse;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductCatalogController implements ProductCatalogApi {
    private final ProductCatalogService catalogService;
    private final DeviceCatalogOptionService optionService;

    public ProductCatalogController(
            ProductCatalogService catalogService, DeviceCatalogOptionService optionService) {
        this.catalogService = catalogService;
        this.optionService = optionService;
    }

    @Override
    public ResponseEntity<ApiResponse<DeviceModelOptionsResponse>> getDeviceModelOptions(
            Long deviceModelId,
            String color,
            String storageGb,
            String memoryGb,
            String screenSizeInches,
            String cpu,
            String gpu,
            String connectivity) {
        Map<DeviceVariantAxis, String> selection = new EnumMap<>(DeviceVariantAxis.class);
        putIfPresent(selection, DeviceVariantAxis.COLOR, color);
        putIfPresent(selection, DeviceVariantAxis.STORAGE_GB, storageGb);
        putIfPresent(selection, DeviceVariantAxis.MEMORY_GB, memoryGb);
        putIfPresent(selection, DeviceVariantAxis.SCREEN_SIZE_INCHES, screenSizeInches);
        putIfPresent(selection, DeviceVariantAxis.CPU, cpu);
        putIfPresent(selection, DeviceVariantAxis.GPU, gpu);
        putIfPresent(selection, DeviceVariantAxis.CONNECTIVITY, connectivity);
        return ResponseEntity.ok(
                ApiResponse.ok(optionService.options(deviceModelId, selection)));
    }

    private void putIfPresent(
            Map<DeviceVariantAxis, String> selection, DeviceVariantAxis axis, String value) {
        if (value != null && !value.isBlank()) selection.put(axis, value);
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
