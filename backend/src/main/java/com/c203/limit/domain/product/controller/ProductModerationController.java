package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.product.moderation.dto.request.CreateListingReportRequest;
import com.c203.limit.domain.product.moderation.dto.request.CreateRestorationRequest;
import com.c203.limit.domain.product.moderation.dto.response.ListingReportCreatedResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationActionResponse;
import com.c203.limit.domain.product.moderation.dto.response.RestorationRequestResponse;
import com.c203.limit.domain.product.moderation.service.ListingModerationService;
import com.c203.limit.domain.seller.service.SellerStatusReader;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductModerationController implements ProductModerationApi {
    private final ListingModerationService moderationService;
    private final CurrentUser currentUser;
    private final SellerStatusReader sellerStatusReader;

    public ProductModerationController(
            ListingModerationService moderationService,
            CurrentUser currentUser,
            SellerStatusReader sellerStatusReader) {
        this.moderationService = moderationService;
        this.currentUser = currentUser;
        this.sellerStatusReader = sellerStatusReader;
    }

    @Override
    public ResponseEntity<ApiResponse<ListingReportCreatedResponse>> report(
            Long productId, CreateListingReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(moderationService.report(
                        currentUser.memberId(), productId, request)));
    }

    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ModerationActionResponse>> acknowledgeWarning(
            Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(
                moderationService.acknowledgeWarning(currentSellerId(), productId)));
    }

    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<RestorationRequestResponse>> requestRestoration(
            Long productId, CreateRestorationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(moderationService.requestRestoration(
                        currentSellerId(), productId, request)));
    }

    private Long currentSellerId() {
        Long memberId = currentUser.memberId();
        sellerStatusReader.requireActiveSeller(memberId);
        return memberId;
    }
}
