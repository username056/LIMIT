package com.c203.limit.domain.admin.controller;

import com.c203.limit.domain.admin.dto.request.AdminLoginRequest;
import com.c203.limit.domain.admin.dto.request.ChangeAdminPasswordRequest;
import com.c203.limit.domain.admin.dto.request.CreateAdminAccountRequest;
import com.c203.limit.domain.admin.dto.request.CreateMemberRestrictionRequest;
import com.c203.limit.domain.admin.dto.request.ReleaseMemberRestrictionRequest;
import com.c203.limit.domain.admin.dto.request.UpdateAdminAccountAccessRequest;
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
}
