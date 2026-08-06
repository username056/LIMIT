package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.product.dto.request.CompleteListingImageRequest;
import com.c203.limit.domain.product.dto.request.CreateListingImageUploadUrlRequest;
import com.c203.limit.domain.product.dto.request.UpdateListingImageOrderRequest;
import com.c203.limit.domain.product.dto.response.EvidenceUploadUrlResponse;
import com.c203.limit.domain.product.dto.response.ListingImageResponse;
import com.c203.limit.domain.product.service.ListingImageUploadService;
import com.c203.limit.domain.seller.service.SellerStatusReader;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductImageController implements ProductImageApi {

    private final ListingImageUploadService service;
    private final CurrentUser currentUser;
    private final SellerStatusReader sellerStatusReader;

    public ProductImageController(
            ListingImageUploadService service,
            CurrentUser currentUser,
            SellerStatusReader sellerStatusReader) {
        this.service = service;
        this.currentUser = currentUser;
        this.sellerStatusReader = sellerStatusReader;
    }

    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<EvidenceUploadUrlResponse>> createImageUploadUrl(
            Long productId, CreateListingImageUploadUrlRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.createUploadUrl(
                        currentSellerId(), productId, request)));
    }

    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ListingImageResponse>> completeImage(
            Long productId, CompleteListingImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.complete(currentSellerId(), productId, request)));
    }

    @Override
    public ResponseEntity<ApiResponse<List<ListingImageResponse>>> getImages(Long productId) {
        return ResponseEntity.ok(
                ApiResponse.ok(service.findAll(productId, currentUser.memberIdOrNull())));
    }

    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<List<ListingImageResponse>>> updateImageOrder(
            Long productId, UpdateListingImageOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.updateOrder(
                currentSellerId(), productId, request)));
    }

    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<Void> deleteImage(Long productId, Long imageId) {
        service.delete(currentSellerId(), productId, imageId);
        return ResponseEntity.noContent().build();
    }

    private Long currentSellerId() {
        Long memberId = currentUser.memberId();
        sellerStatusReader.requireActiveSeller(memberId);
        return memberId;
    }
}
