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
import com.c203.limit.domain.admin.dto.request.UpdateDeviceModelStatusRequest;
import com.c203.limit.domain.inspection.enums.ModelChecklistResearchStatus;
import com.c203.limit.domain.product.entity.DeviceModelRequestStatus;
import com.c203.limit.domain.product.entity.DeviceModelReviewStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "03. 관리자")
@RequestMapping("/api/v1/admin")
public interface AdminApi {
    @Operation(summary = "관리자 로그인")
    @PostMapping("/sessions")
    ResponseEntity<?> login(@Valid @RequestBody AdminLoginRequest request);

    @Operation(summary = "관리자 본인 비밀번호 변경", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/me/password")
    ResponseEntity<?> changeOwnPassword(@Valid @RequestBody ChangeAdminPasswordRequest request);

    @Operation(summary = "회원 목록 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/members")
    ResponseEntity<?> listMembers(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size);

    @Operation(summary = "회원 상세 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/members/{memberId}")
    ResponseEntity<?> getMember(@PathVariable("memberId") Long memberId);

    @Operation(summary = "회원 제재 목록 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/members/{memberId}/restrictions")
    ResponseEntity<?> listRestrictions(
            @PathVariable("memberId") Long memberId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size);

    @Operation(summary = "회원 제재 등록", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "회원 제재 등록 성공")
    @PostMapping("/members/{memberId}/restrictions")
    ResponseEntity<?> createRestriction(
            @PathVariable("memberId") Long memberId,
            @Valid @RequestBody CreateMemberRestrictionRequest request);

    @Operation(summary = "회원 제재 해제", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/member-restrictions/{restrictionId}")
    ResponseEntity<?> releaseRestriction(
            @PathVariable("restrictionId") Long restrictionId,
            @Valid @RequestBody ReleaseMemberRestrictionRequest request);

    @Operation(summary = "관리자 작업 로그 목록 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/action-logs")
    ResponseEntity<?> listActionLogs(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size);

    @Operation(summary = "관리자 작업 로그 상세 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/action-logs/{actionLogId}")
    ResponseEntity<?> getActionLog(@PathVariable("actionLogId") Long actionLogId);

    @Operation(summary = "관리자 작업 로그 사유 수정", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/action-logs/{actionLogId}")
    ResponseEntity<?> updateActionLog(
            @PathVariable("actionLogId") Long actionLogId,
            @Valid @RequestBody UpdateAdminActionLogRequest request);

    @Operation(summary = "관리자 계정 목록 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/accounts")
    ResponseEntity<?> listAdminAccounts(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size);

    @Operation(summary = "관리자 계정 생성", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "관리자 계정 생성 성공")
    @PostMapping("/accounts")
    ResponseEntity<?> createAdminAccount(@Valid @RequestBody CreateAdminAccountRequest request);

    @Operation(summary = "관리자 계정 권한·상태 변경", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/accounts/{adminId}")
    ResponseEntity<?> updateAdminAccount(
            @PathVariable("adminId") Long adminId,
            @Valid @RequestBody UpdateAdminAccountAccessRequest request);

    @Operation(
            summary = "모델 체크리스트 AI 조사 목록 조회",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/checklist-researches")
    ResponseEntity<?> listChecklistResearches(
            @RequestParam(required = false) ModelChecklistResearchStatus status);

    @Operation(
            summary = "모델 체크리스트 AI 조사 검토 완료",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/checklist-researches/{researchId}/approval")
    ResponseEntity<?> approveChecklistResearch(
            @PathVariable("researchId") Long researchId,
            @Valid @RequestBody ReviewChecklistResearchRequest request);

    @Operation(summary = "모델 체크리스트 AI 조사 반려", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/checklist-researches/{researchId}/rejection")
    ResponseEntity<?> rejectChecklistResearch(
            @PathVariable("researchId") Long researchId,
            @Valid @RequestBody ReviewChecklistResearchRequest request);

    @Operation(
            summary = "실패한 모델 체크리스트 AI 조사 재시도",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/checklist-researches/{researchId}/retry")
    ResponseEntity<?> retryChecklistResearch(@PathVariable("researchId") Long researchId);

    @Operation(summary = "관리자 기기 모델 목록 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/device-models")
    ResponseEntity<?> listAdminDeviceModels(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long manufacturerId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) DeviceModelReviewStatus reviewStatus,
            @RequestParam(required = false) ModelChecklistResearchStatus researchStatus,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort);

    @Operation(summary = "관리자 기기 모델 상세 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/device-models/{modelId}")
    ResponseEntity<?> getAdminDeviceModel(@PathVariable("modelId") Long modelId);

    @Operation(summary = "관리자 기기 모델 수정 완료", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/device-models/{modelId}")
    ResponseEntity<?> updateAdminDeviceModel(
            @PathVariable("modelId") Long modelId,
            @Valid @RequestBody UpdateDeviceModelRequest request);

    @Operation(summary = "관리자 기기 모델 활성 상태 변경", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/device-models/{modelId}/status")
    ResponseEntity<?> updateAdminDeviceModelStatus(
            @PathVariable("modelId") Long modelId,
            @Valid @RequestBody UpdateDeviceModelStatusRequest request);

    @Operation(summary = "관리자 기기 모델 연관 상품 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/device-models/{modelId}/products")
    ResponseEntity<?> listAdminDeviceModelProducts(
            @PathVariable("modelId") Long modelId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort);

    @Operation(summary = "관리자 기기 모델 조사 이력 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/device-models/{modelId}/researches")
    ResponseEntity<?> listAdminDeviceModelResearches(
            @PathVariable("modelId") Long modelId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size);

    @Operation(summary = "관리자 연관 상품 자료 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/device-models/{modelId}/products/{productId}/materials")
    ResponseEntity<?> getAdminDeviceModelProductMaterials(
            @PathVariable("modelId") Long modelId, @PathVariable("productId") Long productId);

    @Operation(summary = "관리자 기기 모델 AI 재조사", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/device-models/{modelId}/researches")
    ResponseEntity<?> researchAdminDeviceModel(@PathVariable("modelId") Long modelId);

    @Operation(
            summary = "직접 입력 기기 모델 요청 목록 조회",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/device-model-requests")
    ResponseEntity<?> listDeviceModelRequests(
            @RequestParam(required = false) DeviceModelRequestStatus status);

    @Operation(summary = "직접 입력 기기 모델 요청 수정", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/device-model-requests/{requestId}")
    ResponseEntity<?> updateDeviceModelRequest(
            @PathVariable("requestId") Long requestId,
            @Valid @RequestBody UpdateDeviceModelRequest request);

    @Operation(
            summary = "직접 입력 기기 모델 사후 검토 완료",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/device-model-requests/{requestId}/approval")
    ResponseEntity<?> approveDeviceModelRequest(
            @PathVariable("requestId") Long requestId,
            @Valid @RequestBody ReviewDeviceModelRequest request);

    @Operation(summary = "직접 입력 기기 모델 요청 반려", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/device-model-requests/{requestId}/rejection")
    ResponseEntity<?> rejectDeviceModelRequest(
            @PathVariable("requestId") Long requestId,
            @Valid @RequestBody ReviewDeviceModelRequest request);

    @Operation(
            summary = "결제 PG 대사(reconcile)",
            description =
                    "REQUESTED로 남은 결제를 Toss 실제 승인 여부로 재확인해 필요하면 로컬 상태를 "
                            + "복구한다. confirm 응답을 받지 못해 승인 흔적이 없는 결제를 수동으로 복구할 때 사용한다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/payments/{paymentId}/reconcile")
    ResponseEntity<?> reconcilePayment(@PathVariable("paymentId") Long paymentId);
}
