package com.c203.limit.domain.admin.controller;

import com.c203.limit.domain.admin.dto.request.AdminLoginRequest;
import com.c203.limit.domain.admin.dto.request.ChangeAdminPasswordRequest;
import com.c203.limit.domain.admin.dto.request.CreateAdminAccountRequest;
import com.c203.limit.domain.admin.dto.request.CreateMemberRestrictionRequest;
import com.c203.limit.domain.admin.dto.request.ReleaseMemberRestrictionRequest;
import com.c203.limit.domain.admin.dto.request.ReviewChecklistResearchRequest;
import com.c203.limit.domain.admin.dto.request.ReviewDeviceModelRequest;
import com.c203.limit.domain.admin.dto.request.UpdateAdminAccountAccessRequest;
import com.c203.limit.domain.admin.dto.request.UpdateAdminActionLogRequest;
import com.c203.limit.domain.admin.dto.request.UpdateDeviceModelRequest;
import com.c203.limit.domain.admin.service.AdminAccountManagementService;
import com.c203.limit.domain.admin.service.AdminService;
import com.c203.limit.domain.auth.service.AuthCookieService;
import com.c203.limit.domain.inspection.enums.ModelChecklistResearchStatus;
import com.c203.limit.domain.inspection.service.ModelChecklistResearchService;
import com.c203.limit.domain.payment.service.PaymentService;
import com.c203.limit.domain.product.entity.DeviceModelRequestStatus;
import com.c203.limit.domain.product.entity.DeviceModelReviewStatus;
import com.c203.limit.domain.product.service.DeviceModelManagementService;
import com.c203.limit.domain.product.service.DeviceModelRequestService;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminController implements AdminApi {
    private final AdminService adminService;
    private final AdminAccountManagementService adminAccountManagementService;
    private final ModelChecklistResearchService checklistResearchService;
    private final DeviceModelRequestService deviceModelRequestService;
    private final DeviceModelManagementService deviceModelManagementService;
    private final PaymentService paymentService;
    private final CurrentUser currentUser;
    private final AuthCookieService authCookieService;

    @Override
    public ResponseEntity<?> login(AdminLoginRequest request) {
        var result = adminService.login(request.email(), request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookieService.refresh(result.refreshToken()))
                .body(ApiResponse.ok(result.body()));
    }

    @Override
    public ResponseEntity<?> changeOwnPassword(ChangeAdminPasswordRequest request) {
        adminService.changeOwnPassword(currentUser.adminId(), request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookieService.clearRefresh())
                .build();
    }

    @Override
    public ResponseEntity<?> listMembers(Integer page, Integer size) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.members(page, size)));
    }

    @Override
    public ResponseEntity<?> getMember(Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.member(memberId)));
    }

    @Override
    public ResponseEntity<?> listRestrictions(Long memberId, Integer page, Integer size) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.restrictions(memberId, page, size)));
    }

    @Override
    public ResponseEntity<?> createRestriction(
            Long memberId, CreateMemberRestrictionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.ok(
                                adminService.restrict(currentUser.adminId(), memberId, request)));
    }

    @Override
    public ResponseEntity<?> releaseRestriction(
            Long restrictionId, ReleaseMemberRestrictionRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        adminService.release(
                                currentUser.adminId(), restrictionId, request.releaseReason())));
    }

    @Override
    public ResponseEntity<?> listActionLogs(Integer page, Integer size) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.logs(page, size)));
    }

    @Override
    public ResponseEntity<?> getActionLog(Long actionLogId) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.log(actionLogId)));
    }

    @Override
    public ResponseEntity<?> updateActionLog(
            Long actionLogId, UpdateAdminActionLogRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        adminService.updateLog(
                                currentUser.adminId(), actionLogId, request.reason())));
    }

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> listAdminAccounts(Integer page, Integer size) {
        return ResponseEntity.ok(ApiResponse.ok(adminAccountManagementService.list(page, size)));
    }

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> createAdminAccount(CreateAdminAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.ok(
                                adminAccountManagementService.create(
                                        currentUser.adminId(), request)));
    }

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> updateAdminAccount(
            Long adminId, UpdateAdminAccountAccessRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        adminAccountManagementService.update(
                                currentUser.adminId(), adminId, request)));
    }

    @Override
    public ResponseEntity<?> listChecklistResearches(ModelChecklistResearchStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(checklistResearchService.list(status)));
    }

    @Override
    public ResponseEntity<?> approveChecklistResearch(
            Long researchId, ReviewChecklistResearchRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        checklistResearchService.approve(
                                researchId,
                                currentUser.adminId(),
                                request.approvedFeatureCodes(),
                                request.note())));
    }

    @Override
    public ResponseEntity<?> rejectChecklistResearch(
            Long researchId, ReviewChecklistResearchRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        checklistResearchService.reject(
                                researchId, currentUser.adminId(), request.note())));
    }

    @Override
    public ResponseEntity<?> retryChecklistResearch(Long researchId) {
        return ResponseEntity.ok(
                ApiResponse.ok(checklistResearchService.retry(researchId, currentUser.adminId())));
    }

    @Override
    public ResponseEntity<?> listAdminDeviceModels(DeviceModelReviewStatus reviewStatus) {
        return ResponseEntity.ok(ApiResponse.ok(deviceModelManagementService.list(reviewStatus)));
    }

    @Override
    public ResponseEntity<?> getAdminDeviceModel(Long modelId) {
        return ResponseEntity.ok(ApiResponse.ok(deviceModelManagementService.detail(modelId)));
    }

    @Override
    public ResponseEntity<?> updateAdminDeviceModel(
            Long modelId, UpdateDeviceModelRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        deviceModelManagementService.update(
                                modelId, currentUser.adminId(), request)));
    }

    @Override
    public ResponseEntity<?> researchAdminDeviceModel(Long modelId) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        checklistResearchService.researchModel(modelId, currentUser.adminId())));
    }

    @Override
    public ResponseEntity<?> listDeviceModelRequests(DeviceModelRequestStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(deviceModelRequestService.list(status)));
    }

    @Override
    public ResponseEntity<?> updateDeviceModelRequest(
            Long requestId, UpdateDeviceModelRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        deviceModelRequestService.update(
                                requestId, currentUser.adminId(), request)));
    }

    @Override
    public ResponseEntity<?> approveDeviceModelRequest(
            Long requestId, ReviewDeviceModelRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        deviceModelRequestService.approve(
                                requestId, currentUser.adminId(), request.note())));
    }

    @Override
    public ResponseEntity<?> rejectDeviceModelRequest(
            Long requestId, ReviewDeviceModelRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        deviceModelRequestService.reject(
                                requestId, currentUser.adminId(), request.note())));
    }

    @Override
    public ResponseEntity<?> reconcilePayment(Long paymentId) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.reconcile(paymentId)));
    }
}
