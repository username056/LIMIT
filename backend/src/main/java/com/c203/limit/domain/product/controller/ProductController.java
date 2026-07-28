package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.product.dto.request.CreateProductRequest;
import com.c203.limit.domain.product.dto.request.TransitionProductStatusRequest;
import com.c203.limit.domain.product.dto.request.UpdateProductRequest;
import com.c203.limit.domain.product.dto.response.MyProductSummaryResponse;
import com.c203.limit.domain.product.dto.response.ProductCreatedResponse;
import com.c203.limit.domain.product.dto.response.ProductDetailResponse;
import com.c203.limit.domain.product.dto.response.ProductStatusTransitionResponse;
import com.c203.limit.domain.product.dto.response.ProductSummaryResponse;
import com.c203.limit.domain.product.service.ProductApplicationService;
import com.c203.limit.domain.product.service.ProductApplicationService.MyProductPage;
import com.c203.limit.domain.product.service.ProductApplicationService.ProductPage;
import com.c203.limit.domain.seller.service.SellerStatusReader;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.response.PageMetaResponse;
import com.c203.limit.global.security.CurrentUser;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController implements ProductApi {
    private final ProductApplicationService productService;
    private final CurrentUser currentUser;
    private final SellerStatusReader sellerStatusReader;

    public ProductController(
            ProductApplicationService productService,
            CurrentUser currentUser,
            SellerStatusReader sellerStatusReader) {
        this.productService = productService;
        this.currentUser = currentUser;
        this.sellerStatusReader = sellerStatusReader;
    }

    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductCreatedResponse>> createProduct(
            CreateProductRequest request) {
        ProductCreatedResponse response = productService.create(currentSellerMemberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProduct(
            Long productId, UpdateProductRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(productService.update(currentSellerMemberId(), productId, request)));
    }

    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<Void> deleteProduct(Long productId) {
        productService.delete(currentSellerMemberId(), productId);
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
            String sort) {
        ProductPage result = productService.findPublic(
                keyword,
                categoryId,
                manufacturerId,
                deviceModelId,
                minPrice,
                maxPrice,
                tradeRegion,
                verificationStatus,
                page,
                size,
                sort);
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
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProduct(Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(productService.findPublicDetail(productId)));
    }

    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getMyProduct(Long productId) {
        return ResponseEntity.ok(
                ApiResponse.ok(productService.findOwnedDetail(currentSellerMemberId(), productId)));
    }

    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<List<MyProductSummaryResponse>>> getMyProducts(
            String status, int page, int size, String sort) {
        MyProductPage result =
                productService.findMine(currentSellerMemberId(), status, page, size, sort);
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
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductStatusTransitionResponse>> transitionProductStatus(
            Long productId, TransitionProductStatusRequest request) {
        ProductStatusTransitionResponse response =
                productService.transition(currentSellerMemberId(), productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    private Long currentSellerMemberId() {
        Long memberId = currentUser.memberId();
        sellerStatusReader.requireActiveSeller(memberId);
        return memberId;
    }
}
