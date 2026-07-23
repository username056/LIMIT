package com.c203.limit.domain.product.controller;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.c203.limit.domain.product.dto.request.CreateProductRequest;
import com.c203.limit.domain.product.dto.request.TransitionProductStatusRequest;
import com.c203.limit.domain.product.dto.request.UpdateProductRequest;
import com.c203.limit.domain.product.dto.response.ChecklistSummaryResponse;
import com.c203.limit.domain.product.dto.response.DeviceCategoryResponse;
import com.c203.limit.domain.product.dto.response.DeviceInfoResponse;
import com.c203.limit.domain.product.dto.response.MyProductSummaryResponse;
import com.c203.limit.domain.product.dto.response.ProductCreatedResponse;
import com.c203.limit.domain.product.dto.response.ProductDetailResponse;
import com.c203.limit.domain.product.dto.response.ProductStatusTransitionResponse;
import com.c203.limit.domain.product.dto.response.ProductSummaryResponse;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.response.PageMetaResponse;

@RestController
public class ProductController implements ProductApi {

    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-07-22T10:00:00+09:00");
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-07-22T11:00:00+09:00");

    @Override
    public ResponseEntity<ApiResponse<ProductCreatedResponse>> createProduct(CreateProductRequest request) {
        ProductCreatedResponse response = new ProductCreatedResponse(
                1001L, "DRAFT", request.getDeviceModelId(), 1, 18, 0, CREATED_AT
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Override
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProduct(Long productId, UpdateProductRequest request) {
        String name = request.getName() == null ? "Galaxy S24 256GB" : request.getName();
        String description = request.getDescription() == null ? "생활 흠집이 있습니다." : request.getDescription();
        BigDecimal price = request.getPrice() == null ? BigDecimal.valueOf(650_000) : request.getPrice();
        String color = request.getColor() == null ? "Onyx Black" : request.getColor();
        Integer storageGb = request.getStorageGb() == null ? 256 : request.getStorageGb();
        String tradeRegion = request.getTradeRegion() == null ? "서울 강남구" : request.getTradeRegion();
        return ResponseEntity.ok(ApiResponse.ok(detail(
                productId, name, description, price, "DRAFT", color, storageGb, tradeRegion
        )));
    }

    @Override
    public ResponseEntity<Void> deleteProduct(Long productId) {
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getProducts(
            String keyword,
            Long categoryId,
            Long manufacturerId,
            Long deviceModelId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String tradeRegion,
            String verificationStatus,
            int page,
            int size,
            String sort
    ) {
        ProductSummaryResponse product = new ProductSummaryResponse(
                1001L,
                "Galaxy S24 256GB",
                "Samsung",
                "Galaxy S24",
                BigDecimal.valueOf(650_000),
                "ON_SALE",
                "COMPLETED",
                "https://cdn.example.com/products/1001/thumbnail.jpg",
                "서울 강남구"
        );
        return ResponseEntity.ok(new ApiResponse<>(
                List.of(product), new PageMetaResponse(page, size, 1, 1, false)
        ));
    }

    @Override
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProduct(Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(detail(
                productId,
                "Galaxy S24 256GB",
                "생활 흠집이 있습니다.",
                BigDecimal.valueOf(650_000),
                "ON_SALE",
                "Onyx Black",
                256,
                "서울 강남구"
        )));
    }

    @Override
    public ResponseEntity<ApiResponse<List<MyProductSummaryResponse>>> getMyProducts(
            String status,
            int page,
            int size,
            String sort
    ) {
        MyProductSummaryResponse product = new MyProductSummaryResponse(
                1001L, "Galaxy S24 256GB", status == null ? "VERIFYING" : status, 12, 18, UPDATED_AT
        );
        return ResponseEntity.ok(new ApiResponse<>(
                List.of(product), new PageMetaResponse(page, size, 1, 1, false)
        ));
    }

    @Override
    public ResponseEntity<ApiResponse<ProductStatusTransitionResponse>> transitionProductStatus(
            Long productId,
            TransitionProductStatusRequest request
    ) {
        ProductStatusTransitionResponse response = new ProductStatusTransitionResponse(
                3001L,
                productId,
                "VERIFYING",
                request.getTargetStatus(),
                request.getReason(),
                OffsetDateTime.parse("2026-07-22T12:00:00+09:00")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    private ProductDetailResponse detail(
            Long productId,
            String name,
            String description,
            BigDecimal price,
            String status,
            String color,
            Integer storageGb,
            String tradeRegion
    ) {
        return new ProductDetailResponse(
                productId,
                55L,
                new DeviceCategoryResponse(1L, "SMARTPHONE_BAR", "일반형 스마트폰", null, true),
                new DeviceInfoResponse(101L, "Samsung", "Galaxy S24", "ANDROID", color, storageGb),
                name,
                description,
                price,
                status,
                tradeRegion,
                new ChecklistSummaryResponse(18, status.equals("ON_SALE") ? 18 : 12, 0),
                "https://cdn.example.com/products/1001/thumbnail.jpg",
                CREATED_AT,
                UPDATED_AT
        );
    }
}
