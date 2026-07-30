package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.product.dto.request.CompleteListingImageRequest;
import com.c203.limit.domain.product.dto.request.CreateListingImageUploadUrlRequest;
import com.c203.limit.domain.product.dto.request.UpdateListingImageOrderRequest;
import com.c203.limit.domain.product.dto.response.EvidenceUploadUrlResponse;
import com.c203.limit.domain.product.dto.response.ListingImageResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

public interface ProductImageApi {

    @Operation(summary = "상품 이미지 업로드 URL 발급", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/products/{productId}/images/upload-urls")
    ResponseEntity<ApiResponse<EvidenceUploadUrlResponse>> createImageUploadUrl(
            @PathVariable Long productId,
            @Valid @RequestBody CreateListingImageUploadUrlRequest request);

    @Operation(summary = "상품 이미지 업로드 완료", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/products/{productId}/images")
    ResponseEntity<ApiResponse<ListingImageResponse>> completeImage(
            @PathVariable Long productId,
            @Valid @RequestBody CompleteListingImageRequest request);

    @Operation(summary = "상품 이미지 목록")
    @GetMapping("/api/v1/products/{productId}/images")
    ResponseEntity<ApiResponse<List<ListingImageResponse>>> getImages(
            @PathVariable Long productId);

    @Operation(summary = "상품 이미지 순서 및 대표 이미지 변경", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/api/v1/products/{productId}/images/order")
    ResponseEntity<ApiResponse<List<ListingImageResponse>>> updateImageOrder(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateListingImageOrderRequest request);

    @Operation(summary = "상품 이미지 삭제", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/api/v1/products/{productId}/images/{imageId}")
    ResponseEntity<Void> deleteImage(
            @PathVariable Long productId, @PathVariable Long imageId);
}
