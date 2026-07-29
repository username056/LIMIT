package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.inspection.checklist.ChecklistGenerationService;
import com.c203.limit.domain.inspection.checklist.GeneratedChecklist;
import com.c203.limit.domain.product.dto.request.GenerateChecklistRequest;
import com.c203.limit.domain.product.dto.response.ChecklistGenerationResponse;
import com.c203.limit.domain.seller.service.SellerStatusReader;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChecklistGenerationController implements ChecklistGenerationApi {
    private final ChecklistGenerationService generationService;
    private final CurrentUser currentUser;
    private final SellerStatusReader sellerStatusReader;

    public ChecklistGenerationController(
            ChecklistGenerationService generationService,
            CurrentUser currentUser,
            SellerStatusReader sellerStatusReader) {
        this.generationService = generationService;
        this.currentUser = currentUser;
        this.sellerStatusReader = sellerStatusReader;
    }

    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ChecklistGenerationResponse>> generateChecklist(
            GenerateChecklistRequest request) {
        sellerStatusReader.requireActiveSeller(currentUser.memberId());
        GeneratedChecklist generated = request.deviceModelId() == null
                ? generationService.generateCustom(
                        request.manufacturer(),
                        request.modelName(),
                        request.modelCode(),
                        request.osFamily(),
                        request.confirmedFeatures())
                : generationService.generateForModel(
                        request.deviceModelId(), request.confirmedFeatures());
        return ResponseEntity.ok(ApiResponse.ok(ChecklistGenerationResponse.from(generated)));
    }
}
