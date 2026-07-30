package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.product.dto.request.UpdateProductDraftProgressRequest;
import com.c203.limit.domain.product.dto.response.ProductDraftProgressResponse;
import com.c203.limit.domain.product.service.ProductDraftProgressService;
import com.c203.limit.domain.seller.service.SellerStatusReader;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductDraftProgressController {
    private final ProductDraftProgressService service;
    private final CurrentUser currentUser;
    private final SellerStatusReader sellerStatusReader;

    public ProductDraftProgressController(
            ProductDraftProgressService service,
            CurrentUser currentUser,
            SellerStatusReader sellerStatusReader) {
        this.service = service;
        this.currentUser = currentUser;
        this.sellerStatusReader = sellerStatusReader;
    }

    @GetMapping("/api/v1/products/{productId}/draft-progress")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(summary = "상품 임시저장 진행 상태 조회", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<ProductDraftProgressResponse>> get(
            @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(service.find(sellerId(), productId)));
    }

    @PatchMapping("/api/v1/products/{productId}/draft-progress")
    @PreAuthorize("hasRole('SELLER')")
    @Operation(summary = "상품 임시저장 진행 상태 저장", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<ProductDraftProgressResponse>> update(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateProductDraftProgressRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(sellerId(), productId, request)));
    }

    private Long sellerId() {
        Long memberId = currentUser.memberId();
        sellerStatusReader.requireActiveSeller(memberId);
        return memberId;
    }
}
