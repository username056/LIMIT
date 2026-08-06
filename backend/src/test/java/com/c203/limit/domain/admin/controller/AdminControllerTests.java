package com.c203.limit.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.c203.limit.domain.admin.dto.request.UpdateDeviceModelStatusRequest;
import com.c203.limit.domain.admin.dto.response.AdminAccountResponse;
import com.c203.limit.domain.admin.dto.response.AdminDeviceModelDetailResponse;
import com.c203.limit.domain.admin.dto.response.AdminDeviceModelSummaryResponse;
import com.c203.limit.domain.admin.dto.response.AdminProductMaterialsResponse;
import com.c203.limit.domain.admin.dto.response.AdminRelatedProductResponse;
import com.c203.limit.domain.admin.dto.response.ChecklistResearchResponse;
import com.c203.limit.domain.admin.service.AdminAccountManagementService;
import com.c203.limit.domain.admin.service.AdminService;
import com.c203.limit.domain.auth.service.AuthCookieService;
import com.c203.limit.domain.auth.service.SessionResult;
import com.c203.limit.domain.inspection.enums.ModelChecklistResearchStatus;
import com.c203.limit.domain.inspection.service.ModelChecklistResearchService;
import com.c203.limit.domain.payment.dto.response.PaymentReconcileResponse;
import com.c203.limit.domain.payment.service.PaymentService;
import com.c203.limit.domain.product.dto.response.DeviceModelRequestResponse;
import com.c203.limit.domain.product.entity.DeviceModelRequestStatus;
import com.c203.limit.domain.product.entity.DeviceModelReviewStatus;
import com.c203.limit.domain.product.entity.OsFamily;
import com.c203.limit.domain.product.service.DeviceModelManagementService;
import com.c203.limit.domain.product.service.DeviceModelRequestService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.response.PageResponse;
import com.c203.limit.global.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminControllerTests {
    private static final Long ADMIN_ID = 7L;
    private static final Long MEMBER_ID = 9L;
    private static final Long MODEL_ID = 55L;

    @Mock AdminService adminService;
    @Mock AdminAccountManagementService adminAccountManagementService;
    @Mock ModelChecklistResearchService checklistResearchService;
    @Mock DeviceModelRequestService deviceModelRequestService;
    @Mock DeviceModelManagementService deviceModelManagementService;
    @Mock PaymentService paymentService;
    @Mock CurrentUser currentUser;
    @Mock AuthCookieService authCookieService;

    AdminController controller;

    @BeforeEach
    void setUp() {
        controller =
                new AdminController(
                        adminService,
                        adminAccountManagementService,
                        checklistResearchService,
                        deviceModelRequestService,
                        deviceModelManagementService,
                        paymentService,
                        currentUser,
                        authCookieService);
    }

    @Test
    void issuesRefreshCookieWhenAdminLoginSucceeds() {
        Map<String, Object> body = Map.of("accessToken", "access-value");
        when(adminService.login("admin@limit.local", "AdminPassword123!"))
                .thenReturn(new SessionResult<>(body, "refresh-value"));
        when(authCookieService.refresh("refresh-value")).thenReturn("refresh=refresh-value");

        ResponseEntity<?> response =
                controller.login(
                        new AdminLoginRequest("admin@limit.local", "AdminPassword123!"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .containsExactly("refresh=refresh-value");
        assertThat(data(response)).isSameAs(body);
    }

    @Test
    void doesNotIssueCookieWhenAdminLoginFails() {
        when(adminService.login("admin@limit.local", "wrong"))
                .thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        assertThatThrownBy(
                        () ->
                                controller.login(
                                        new AdminLoginRequest("admin@limit.local", "wrong")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        verifyNoInteractions(authCookieService);
    }

    @Test
    void clearsRefreshCookieAfterOwnPasswordChange() {
        ChangeAdminPasswordRequest request =
                new ChangeAdminPasswordRequest("OldAdminPassword123!", "NewAdminPassword456!");
        when(currentUser.adminId()).thenReturn(ADMIN_ID);
        when(authCookieService.clearRefresh()).thenReturn("refresh=; Max-Age=0");

        ResponseEntity<?> response = controller.changeOwnPassword(request);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .containsExactly("refresh=; Max-Age=0");
        verify(adminService).changeOwnPassword(ADMIN_ID, request);
    }

    @Test
    void listsMembersWithGivenPageParameters() {
        PageResponse<Map<String, Object>> page = page(List.of(Map.of("memberId", MEMBER_ID)));
        when(adminService.members(2, 50)).thenReturn(page);

        ResponseEntity<?> response = controller.listMembers(2, 50);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(data(response)).isSameAs(page);
    }

    @Test
    void listsMembersWithNullPageParameters() {
        PageResponse<Map<String, Object>> page = page(List.of());
        when(adminService.members(null, null)).thenReturn(page);

        assertThat(data(controller.listMembers(null, null))).isSameAs(page);
    }

    @Test
    void returnsSingleMemberDetail() {
        Map<String, Object> member = Map.of("memberId", MEMBER_ID);
        when(adminService.member(MEMBER_ID)).thenReturn(member);

        assertThat(data(controller.getMember(MEMBER_ID))).isSameAs(member);
    }

    @Test
    void propagatesMemberNotFoundFromService() {
        when(adminService.member(MEMBER_ID))
                .thenThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        assertThatThrownBy(() -> controller.getMember(MEMBER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void listsRestrictionsOfMember() {
        PageResponse<Map<String, Object>> page = page(List.of());
        when(adminService.restrictions(MEMBER_ID, 0, 20)).thenReturn(page);

        assertThat(data(controller.listRestrictions(MEMBER_ID, 0, 20))).isSameAs(page);
    }

    @Test
    void returns201WhenRestrictionIsCreated() {
        CreateMemberRestrictionRequest request =
                new CreateMemberRestrictionRequest(
                        "PURCHASE",
                        "MACRO_USE",
                        "비정상 반복 요청",
                        LocalDateTime.of(2026, 7, 22, 14, 0),
                        LocalDateTime.of(2026, 7, 29, 14, 0));
        Map<String, Object> created = Map.of("restrictionId", 3L);
        when(currentUser.adminId()).thenReturn(ADMIN_ID);
        when(adminService.restrict(ADMIN_ID, MEMBER_ID, request)).thenReturn(created);

        ResponseEntity<?> response = controller.createRestriction(MEMBER_ID, request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(data(response)).isSameAs(created);
    }

    @Test
    void passesReleaseReasonToService() {
        Map<String, Object> released = Map.of("status", "RELEASED");
        when(currentUser.adminId()).thenReturn(ADMIN_ID);
        when(adminService.release(ADMIN_ID, 3L, "오탐 확인")).thenReturn(released);

        ResponseEntity<?> response =
                controller.releaseRestriction(3L, new ReleaseMemberRestrictionRequest("오탐 확인"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(data(response)).isSameAs(released);
    }

    @Test
    void listsActionLogs() {
        PageResponse<Map<String, Object>> page = page(List.of());
        when(adminService.logs(1, 10)).thenReturn(page);

        assertThat(data(controller.listActionLogs(1, 10))).isSameAs(page);
    }

    @Test
    void returnsSingleActionLog() {
        Map<String, Object> actionLog = Map.of("adminActionLogId", 4L);
        when(adminService.log(4L)).thenReturn(actionLog);

        assertThat(data(controller.getActionLog(4L))).isSameAs(actionLog);
    }

    @Test
    void passesActionLogReasonToService() {
        Map<String, Object> updated = Map.of("reason", "정정된 사유");
        when(currentUser.adminId()).thenReturn(ADMIN_ID);
        when(adminService.updateLog(ADMIN_ID, 4L, "정정된 사유")).thenReturn(updated);

        assertThat(data(controller.updateActionLog(4L, new UpdateAdminActionLogRequest("정정된 사유"))))
                .isSameAs(updated);
    }

    @Test
    void listsAdminAccounts() {
        PageResponse<AdminAccountResponse> page = page(List.of());
        when(adminAccountManagementService.list(0, 20)).thenReturn(page);

        assertThat(data(controller.listAdminAccounts(0, 20))).isSameAs(page);
    }

    @Test
    void returns201WhenAdminAccountIsCreated() {
        CreateAdminAccountRequest request =
                new CreateAdminAccountRequest(
                        "operator@limit.local", "OperatorPassword1!", "운영자", "OPERATOR");
        AdminAccountResponse created =
                new AdminAccountResponse(
                        11L, "operator@limit.local", "운영자", "OPERATOR", "ACTIVE", null, null, null);
        when(currentUser.adminId()).thenReturn(ADMIN_ID);
        when(adminAccountManagementService.create(ADMIN_ID, request)).thenReturn(created);

        ResponseEntity<?> response = controller.createAdminAccount(request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(data(response)).isSameAs(created);
    }

    @Test
    void updatesAdminAccountAccess() {
        UpdateAdminAccountAccessRequest request =
                new UpdateAdminAccountAccessRequest("SUPER_ADMIN", "ACTIVE");
        AdminAccountResponse updated =
                new AdminAccountResponse(
                        11L,
                        "operator@limit.local",
                        "운영자",
                        "SUPER_ADMIN",
                        "ACTIVE",
                        null,
                        null,
                        null);
        when(currentUser.adminId()).thenReturn(ADMIN_ID);
        when(adminAccountManagementService.update(ADMIN_ID, 11L, request)).thenReturn(updated);

        ResponseEntity<?> response = controller.updateAdminAccount(11L, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(data(response)).isSameAs(updated);
    }

    @Test
    void listsChecklistResearchesByStatus() {
        List<ChecklistResearchResponse> researches = List.of();
        when(checklistResearchService.list(ModelChecklistResearchStatus.PENDING_REVIEW))
                .thenReturn(researches);

        assertThat(data(controller.listChecklistResearches(
                        ModelChecklistResearchStatus.PENDING_REVIEW)))
                .isSameAs(researches);
    }

    @Test
    void approvesChecklistResearchWithSelectedFeatureCodes() {
        ReviewChecklistResearchRequest request =
                new ReviewChecklistResearchRequest(Set.of("BATTERY", "CAMERA"), "확인 완료");
        ChecklistResearchResponse approved = research(ModelChecklistResearchStatus.APPROVED);
        when(currentUser.adminId()).thenReturn(ADMIN_ID);
        when(checklistResearchService.approve(
                        21L, ADMIN_ID, Set.of("BATTERY", "CAMERA"), "확인 완료"))
                .thenReturn(approved);

        assertThat(data(controller.approveChecklistResearch(21L, request))).isSameAs(approved);
    }

    @Test
    void rejectsChecklistResearchWithNoteOnly() {
        ReviewChecklistResearchRequest request =
                new ReviewChecklistResearchRequest(null, "근거 부족");
        ChecklistResearchResponse rejected = research(ModelChecklistResearchStatus.REJECTED);
        when(currentUser.adminId()).thenReturn(ADMIN_ID);
        when(checklistResearchService.reject(21L, ADMIN_ID, "근거 부족")).thenReturn(rejected);

        assertThat(data(controller.rejectChecklistResearch(21L, request))).isSameAs(rejected);
    }

    @Test
    void retriesChecklistResearch() {
        ChecklistResearchResponse retried = research(ModelChecklistResearchStatus.PROCESSING);
        when(currentUser.adminId()).thenReturn(ADMIN_ID);
        when(checklistResearchService.retry(21L, ADMIN_ID)).thenReturn(retried);

        assertThat(data(controller.retryChecklistResearch(21L))).isSameAs(retried);
    }

    @Test
    void passesEveryDeviceModelFilterToService() {
        PageResponse<AdminDeviceModelSummaryResponse> page = page(List.of());
        when(deviceModelManagementService.list(
                        "galaxy",
                        1L,
                        2L,
                        true,
                        DeviceModelReviewStatus.VERIFIED,
                        ModelChecklistResearchStatus.APPROVED,
                        0,
                        20,
                        "createdAt,desc"))
                .thenReturn(page);

        ResponseEntity<?> response =
                controller.listAdminDeviceModels(
                        "galaxy",
                        1L,
                        2L,
                        true,
                        DeviceModelReviewStatus.VERIFIED,
                        ModelChecklistResearchStatus.APPROVED,
                        0,
                        20,
                        "createdAt,desc");

        assertThat(data(response)).isSameAs(page);
    }

    @Test
    void returnsDeviceModelDetail() {
        AdminDeviceModelDetailResponse detail = mock(AdminDeviceModelDetailResponse.class);
        when(deviceModelManagementService.detail(MODEL_ID)).thenReturn(detail);

        assertThat(data(controller.getAdminDeviceModel(MODEL_ID))).isSameAs(detail);
    }

    @Test
    void updatesDeviceModelWithCurrentAdminId() {
        UpdateDeviceModelRequest request =
                new UpdateDeviceModelRequest(1L, "삼성", "갤럭시 S24", "SM-S921N", OsFamily.ANDROID);
        AdminDeviceModelDetailResponse detail = mock(AdminDeviceModelDetailResponse.class);
        when(currentUser.adminId()).thenReturn(ADMIN_ID);
        when(deviceModelManagementService.update(MODEL_ID, ADMIN_ID, request)).thenReturn(detail);

        assertThat(data(controller.updateAdminDeviceModel(MODEL_ID, request))).isSameAs(detail);
    }

    @Test
    void updatesDeviceModelStatusWithCurrentAdminId() {
        UpdateDeviceModelStatusRequest request =
                new UpdateDeviceModelStatusRequest(false, "단종", 99L);
        AdminDeviceModelDetailResponse detail = mock(AdminDeviceModelDetailResponse.class);
        when(currentUser.adminId()).thenReturn(ADMIN_ID);
        when(deviceModelManagementService.updateStatus(MODEL_ID, ADMIN_ID, request))
                .thenReturn(detail);

        assertThat(data(controller.updateAdminDeviceModelStatus(MODEL_ID, request)))
                .isSameAs(detail);
    }

    @Test
    void listsRelatedProductsOfDeviceModel() {
        PageResponse<AdminRelatedProductResponse> page = page(List.of());
        when(deviceModelManagementService.products(MODEL_ID, 0, 20, "createdAt,desc"))
                .thenReturn(page);

        assertThat(data(controller.listAdminDeviceModelProducts(MODEL_ID, 0, 20, "createdAt,desc")))
                .isSameAs(page);
    }

    @Test
    void listsResearchHistoryOfDeviceModel() {
        PageResponse<ChecklistResearchResponse> page = page(List.of());
        when(deviceModelManagementService.researches(MODEL_ID, 0, 20)).thenReturn(page);

        assertThat(data(controller.listAdminDeviceModelResearches(MODEL_ID, 0, 20)))
                .isSameAs(page);
    }

    @Test
    void returnsProductMaterialsOfDeviceModel() {
        AdminProductMaterialsResponse materials =
                new AdminProductMaterialsResponse(31L, List.of(), List.of());
        when(deviceModelManagementService.materials(MODEL_ID, 31L)).thenReturn(materials);

        assertThat(data(controller.getAdminDeviceModelProductMaterials(MODEL_ID, 31L)))
                .isSameAs(materials);
    }

    @Test
    void triggersDeviceModelResearchWithCurrentAdminId() {
        ChecklistResearchResponse started = research(ModelChecklistResearchStatus.PROCESSING);
        when(currentUser.adminId()).thenReturn(ADMIN_ID);
        when(checklistResearchService.researchModel(MODEL_ID, ADMIN_ID)).thenReturn(started);

        assertThat(data(controller.researchAdminDeviceModel(MODEL_ID))).isSameAs(started);
    }

    @Test
    void listsDeviceModelRequestsByStatus() {
        List<DeviceModelRequestResponse> requests = List.of();
        when(deviceModelRequestService.list(DeviceModelRequestStatus.PENDING))
                .thenReturn(requests);

        assertThat(data(controller.listDeviceModelRequests(DeviceModelRequestStatus.PENDING)))
                .isSameAs(requests);
    }

    @Test
    void updatesDeviceModelRequestWithCurrentAdminId() {
        UpdateDeviceModelRequest request =
                new UpdateDeviceModelRequest(1L, "애플", "아이폰 15", null, OsFamily.IOS);
        DeviceModelRequestResponse updated = modelRequest(DeviceModelRequestStatus.PENDING);
        when(currentUser.adminId()).thenReturn(ADMIN_ID);
        when(deviceModelRequestService.update(41L, ADMIN_ID, request)).thenReturn(updated);

        assertThat(data(controller.updateDeviceModelRequest(41L, request))).isSameAs(updated);
    }

    @Test
    void approvesDeviceModelRequestWithNote() {
        DeviceModelRequestResponse approved = modelRequest(DeviceModelRequestStatus.APPROVED);
        when(currentUser.adminId()).thenReturn(ADMIN_ID);
        when(deviceModelRequestService.approve(41L, ADMIN_ID, "확인")).thenReturn(approved);

        assertThat(data(controller.approveDeviceModelRequest(41L, new ReviewDeviceModelRequest("확인"))))
                .isSameAs(approved);
    }

    @Test
    void rejectsDeviceModelRequestWithNullNote() {
        DeviceModelRequestResponse rejected = modelRequest(DeviceModelRequestStatus.REJECTED);
        when(currentUser.adminId()).thenReturn(ADMIN_ID);
        when(deviceModelRequestService.reject(41L, ADMIN_ID, null)).thenReturn(rejected);

        assertThat(data(controller.rejectDeviceModelRequest(41L, new ReviewDeviceModelRequest(null))))
                .isSameAs(rejected);
    }

    @Test
    void reconcilesPaymentById() {
        PaymentReconcileResponse reconcile = mock(PaymentReconcileResponse.class);
        when(paymentService.reconcile(77L)).thenReturn(reconcile);

        ResponseEntity<?> response = controller.reconcilePayment(77L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(data(response)).isSameAs(reconcile);
    }

    private Object data(ResponseEntity<?> response) {
        return ((ApiResponse<?>) response.getBody()).data();
    }

    private <T> PageResponse<T> page(List<T> content) {
        return new PageResponse<>(content, 0, 20, content.size(), 1, false);
    }

    private ChecklistResearchResponse research(ModelChecklistResearchStatus status) {
        return new ChecklistResearchResponse(
                21L,
                MODEL_ID,
                "SMARTPHONE",
                "삼성",
                "갤럭시 S24",
                1,
                status.name(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                ADMIN_ID,
                null,
                null,
                null);
    }

    private DeviceModelRequestResponse modelRequest(DeviceModelRequestStatus status) {
        return new DeviceModelRequestResponse(
                41L,
                1L,
                "애플",
                "아이폰 15",
                null,
                OsFamily.IOS.name(),
                status.name(),
                null,
                MEMBER_ID,
                null,
                null,
                ADMIN_ID,
                null,
                null);
    }
}
