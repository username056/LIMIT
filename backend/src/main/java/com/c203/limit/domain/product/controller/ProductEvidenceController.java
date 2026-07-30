package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.product.dto.request.CompleteEvidenceRequest;
import com.c203.limit.domain.product.dto.request.CreateEvidenceUploadUrlRequest;
import com.c203.limit.domain.product.dto.response.EvidenceResponse;
import com.c203.limit.domain.product.dto.response.EvidenceUploadUrlResponse;
import com.c203.limit.domain.product.dto.response.ProductChecklistItemResponse;
import com.c203.limit.domain.product.service.EvidenceHistoryService;
import com.c203.limit.domain.product.service.EvidenceUploadService;
import com.c203.limit.domain.product.service.ProductChecklistService;
import com.c203.limit.domain.seller.service.SellerStatusReader;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductEvidenceController implements ProductEvidenceApi {

    private final ProductChecklistService productChecklistService;
    private final EvidenceUploadService evidenceUploadService;
    private final EvidenceHistoryService evidenceHistoryService;
    private final CurrentUser currentUser;
    private final SellerStatusReader sellerStatusReader;

    public ProductEvidenceController(
            ProductChecklistService productChecklistService,
            EvidenceUploadService evidenceUploadService,
            EvidenceHistoryService evidenceHistoryService,
            CurrentUser currentUser,
            SellerStatusReader sellerStatusReader) {
        this.productChecklistService = productChecklistService;
        this.evidenceUploadService = evidenceUploadService;
        this.evidenceHistoryService = evidenceHistoryService;
        this.currentUser = currentUser;
        this.sellerStatusReader = sellerStatusReader;
    }

    @Override
    public ResponseEntity<ApiResponse<List<ProductChecklistItemResponse>>> getProductChecklist(
            Long productId, String status, boolean requiredOnly) {
        return ResponseEntity.ok(
                ApiResponse.ok(productChecklistService.findAll(productId, status, requiredOnly)));
    }

    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<EvidenceUploadUrlResponse>> createEvidenceUploadUrl(
            Long productId,
            Long checklistItemId,
            CreateEvidenceUploadUrlRequest request) {
        EvidenceUploadUrlResponse response =
                evidenceUploadService.createUploadUrl(
                        currentSellerMemberId(), productId, checklistItemId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<EvidenceResponse>> completeEvidence(
            Long productId, Long checklistItemId, CompleteEvidenceRequest request) {
        EvidenceResponse response =
                evidenceUploadService.complete(
                        currentSellerMemberId(), productId, checklistItemId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Override
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> getEvidenceHistory(
            Long productId, Long checklistItemId) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        evidenceHistoryService.findAll(
                                productId, checklistItemId, currentUser.memberIdOrNull())));
    }

    private Long currentSellerMemberId() {
        Long memberId = currentUser.memberId();
        sellerStatusReader.requireActiveSeller(memberId);
        return memberId;
    }
}
