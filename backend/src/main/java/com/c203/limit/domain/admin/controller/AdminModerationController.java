package com.c203.limit.domain.admin.controller;

import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.dto.response.ProductDetailResponse;
import com.c203.limit.domain.product.moderation.dto.request.AdminModerationDecisionRequest;
import com.c203.limit.domain.product.moderation.dto.request.AdminRestorationDecisionRequest;
import com.c203.limit.domain.product.moderation.dto.request.ResolveRiskSignalRequest;
import com.c203.limit.domain.product.moderation.dto.response.AdminListingReportResponse;
import com.c203.limit.domain.product.moderation.dto.response.AdminModeratedProductResponse;
import com.c203.limit.domain.product.moderation.dto.response.AdminRestorationRequestResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationDashboardResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationRiskSignalResponse;
import com.c203.limit.domain.product.moderation.entity.*;
import com.c203.limit.domain.product.moderation.service.ListingModerationService;
import com.c203.limit.domain.product.moderation.service.ModerationRiskService;
import com.c203.limit.domain.product.service.ProductApplicationService;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.response.PageResponse;
import com.c203.limit.global.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminModerationController implements AdminModerationApi {
    private final ListingModerationService moderationService;
    private final ModerationRiskService riskService;
    private final CurrentUser currentUser;
    private final ProductApplicationService productService;

    public AdminModerationController(
            ListingModerationService moderationService,
            ModerationRiskService riskService,
            CurrentUser currentUser,
            ProductApplicationService productService) {
        this.moderationService = moderationService;
        this.riskService = riskService;
        this.currentUser = currentUser;
        this.productService = productService;
    }

    @Override
    public ResponseEntity<ApiResponse<ModerationDashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(riskService.dashboard()));
    }

    @Override
    public ResponseEntity<ApiResponse<PageResponse<AdminListingReportResponse>>> reports(
            ListingReportStatus status, int page, int size) {
        return ResponseEntity.ok(ApiResponse.ok(moderationService.reports(status, page, size)));
    }

    @Override
    public ResponseEntity<ApiResponse<AdminListingReportResponse>> decideReport(
            Long reportId, AdminModerationDecisionRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        moderationService.decideReport(currentUser.adminId(), reportId, request)));
    }

    @Override
    public ResponseEntity<ApiResponse<PageResponse<AdminRestorationRequestResponse>>> restorationRequests(
            RestorationRequestStatus status, int page, int size) {
        return ResponseEntity.ok(
                ApiResponse.ok(moderationService.restorationRequests(status, page, size)));
    }

    @Override
    public ResponseEntity<ApiResponse<AdminRestorationRequestResponse>> decideRestoration(
            Long requestId, AdminRestorationDecisionRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        moderationService.decideRestoration(
                                currentUser.adminId(), requestId, request)));
    }

    @Override
    public ResponseEntity<ApiResponse<PageResponse<ModerationRiskSignalResponse>>> riskSignals(
            ModerationRiskStatus status, ModerationRiskType type, int page, int size) {
        return ResponseEntity.ok(ApiResponse.ok(riskService.signals(status, type, page, size)));
    }

    @Override
    public ResponseEntity<ApiResponse<ModerationRiskSignalResponse>> resolveRiskSignal(
            Long signalId, ResolveRiskSignalRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        riskService.resolve(currentUser.adminId(), signalId, request.note())));
    }

    @Override
    public ResponseEntity<ApiResponse<PageResponse<AdminModeratedProductResponse>>> products(
            String keyword,
            Long sellerId,
            ListingStatus lifecycleStatus,
            ListingModerationStatus moderationStatus,
            int page,
            int size) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        riskService.products(
                                keyword, sellerId, lifecycleStatus, moderationStatus, page, size)));
    }

    @Override
    public ResponseEntity<ApiResponse<ProductDetailResponse>> productDetail(Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(productService.findAdminDetail(productId)));
    }
}
