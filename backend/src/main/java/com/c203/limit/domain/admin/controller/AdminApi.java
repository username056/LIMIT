package com.c203.limit.domain.admin.controller;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "17. 관리자")
public interface AdminApi {

    @Operation(operationId = "adminauth01", summary = "관리자 로그인", description = "요청\n권한: PUBLIC\n검증: OPERATOR 또는 SUPER_ADMIN 역할, 활성 회원 상태, LOGIN 제재 없음")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "관리자 로그인 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/AdminLoginResponse"))),
        @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS"),
        @ApiResponse(responseCode = "403", description = "ADMIN_ROLE_REQUIRED / LOGIN_RESTRICTED"),
        @ApiResponse(responseCode = "429", description = "LOGIN_ATTEMPT_LIMITED")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/admin/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminauth01(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/AdminLoginRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "adminmember04", summary = "회원 이용 제한 등록", description = "요청\n권한: OPERATOR+\nPath: memberId(long)\n검증: 제한 유형, 기간, 중복 활성 제재 정책\n처리: member_restrictions 생성, admin_action_logs 기록", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "회원 이용 제한 등록 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/MemberRestrictionResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_RESTRICTION_PERIOD"),
        @ApiResponse(responseCode = "404", description = "MEMBER_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "OVERLAPPING_RESTRICTION")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/admin/members/{memberId}/restrictions", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminmember04(
            @PathVariable("memberId") Long memberId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CreateMemberRestrictionRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "adminmember05", summary = "회원 이용 제한 해제", description = "요청\n권한: OPERATOR+\nPath: restrictionId(long)\n검증: ACTIVE 상태 제재만 해제 가능\n처리: status RELEASED, released_by·released_at 기록, admin_action_logs 생성", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "회원 이용 제한 해제 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/MemberRestrictionResponse"))),
        @ApiResponse(responseCode = "404", description = "RESTRICTION_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "RESTRICTION_NOT_ACTIVE")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/admin/member-restrictions/{restrictionId}/releases", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminmember05(
            @PathVariable("restrictionId") Long restrictionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/ReleaseMemberRestrictionRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "adminmember01", summary = "회원 목록 조회", description = "요청\n권한: OPERATOR+\nQuery: page, size, email, nickname, status(ACTIVE|WITHDRAWAL_PENDING|WITHDRAWN), role(BUYER|SELLER|OPERATOR|SUPER_ADMIN), createdFrom, createdTo, sort\n개인정보: 목록에서는 연락처 등 민감정보 최소 노출", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "회원 목록 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/AdminMemberSummaryResponse")))),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/members", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminmember01(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "nickname", required = false) String nickname,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "createdFrom", required = false) LocalDate createdFrom,
            @RequestParam(name = "createdTo", required = false) LocalDate createdTo
    );
    @Operation(operationId = "adminmember02", summary = "회원 상세 조회", description = "요청\n권한: OPERATOR+\nPath: memberId(long)\n처리: 회원 기본 정보, 역할, 소셜 연동, 배송지 개수, 활성 제재, 탈퇴 요청 요약 조회\n민감정보: 이메일·전화번호 마스킹, 상세 조회 로그 기록 권장", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "회원 상세 조회 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/AdminMemberDetailResponse"))),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "404", description = "MEMBER_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/members/{memberId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminmember02(
            @PathVariable("memberId") Long memberId
    );
    @Operation(operationId = "adminmember03", summary = "회원 제재 이력 조회", description = "요청\n권한: OPERATOR+\nPath: memberId(long)\nQuery: page, size, status(optional)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "회원 제재 이력 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/MemberRestrictionResponse"))))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/members/{memberId}/restrictions", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminmember03(
            @PathVariable("memberId") Long memberId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status
    );
    @Operation(operationId = "adminwithdrawal01", summary = "회원 탈퇴 요청 목록 조회", description = "요청\n권한: OPERATOR+\nQuery: page, size, status(REQUESTED|BLOCKED|COMPLETED|CANCELED), requestedFrom, requestedTo", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "회원 탈퇴 요청 목록 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/AdminWithdrawalSummaryResponse"))))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/member-withdrawal-requests", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminwithdrawal01(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "requestedFrom", required = false) String requestedFrom,
            @RequestParam(name = "requestedTo", required = false) String requestedTo
    );
    @Operation(operationId = "adminwithdrawal02", summary = "회원 탈퇴 요청 상세 조회", description = "요청\n권한: OPERATOR+\nPath: withdrawalRequestId(long)\n처리: 요청 사유, 제한 사유, 주문·분쟁·정산 등 탈퇴 제한 요약 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "회원 탈퇴 요청 상세 조회 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/AdminWithdrawalDetailResponse"))),
        @ApiResponse(responseCode = "404", description = "WITHDRAWAL_REQUEST_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/member-withdrawal-requests/{withdrawalRequestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminwithdrawal02(
            @PathVariable("withdrawalRequestId") Long withdrawalRequestId
    );
    @Operation(operationId = "adminwithdrawal03", summary = "회원 탈퇴 요청 처리", description = "요청\n권한: OPERATOR+\nPath: withdrawalRequestId(long)\n또는\n{\"decision\":\"BLOCK\",\"blockedReason\":\"진행 중인 분쟁이 존재합니다.\"}\n처리: processed_by·processed_at 기록, 완료 시 members.status WITHDRAWN 및 withdrawn_at 기록, 감사 로그 생성", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "회원 탈퇴 요청 처리 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/WithdrawalProcessResponse"))),
        @ApiResponse(responseCode = "400", description = "BLOCKED_REASON_REQUIRED"),
        @ApiResponse(responseCode = "409", description = "WITHDRAWAL_REQUEST_ALREADY_PROCESSED / WITHDRAWAL_STILL_BLOCKED")
    })
    @RequestMapping(method = RequestMethod.PATCH, path = "/api/v1/admin/member-withdrawal-requests/{withdrawalRequestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminwithdrawal03(
            @PathVariable("withdrawalRequestId") Long withdrawalRequestId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/ProcessWithdrawalRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "adminsellerapp04", summary = "판매자 신청 심사 시작", description = "요청\n권한: OPERATOR+\nPath: applicationId(long)\n처리: SUBMITTED 상태를 UNDER_REVIEW로 변경, reviewed_by 선점 또는 담당자 기록", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "판매자 신청 심사 시작 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/StartSellerReviewResponse"))),
        @ApiResponse(responseCode = "409", description = "APPLICATION_NOT_REVIEWABLE / APPLICATION_ALREADY_ASSIGNED")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/admin/seller-applications/{applicationId}/reviews", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminsellerapp04(
            @PathVariable("applicationId") Long applicationId
    );
    @Operation(operationId = "adminsellerapp05", summary = "판매자 신청 승인", description = "요청\n권한: OPERATOR+\nPath: applicationId(long)\n검증: UNDER_REVIEW 상태, 필수 증빙 확인\n처리: 신청 APPROVED, seller_profiles 생성, SELLER 역할 부여, reviewed_by·reviewed_at 기록, 감사 로그 생성", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "판매자 신청 승인 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/ApproveSellerApplicationResponse"))),
        @ApiResponse(responseCode = "409", description = "APPLICATION_NOT_APPROVABLE / SELLER_PROFILE_ALREADY_EXISTS")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/admin/seller-applications/{applicationId}/approvals", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminsellerapp05(
            @PathVariable("applicationId") Long applicationId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/ApproveSellerApplicationRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "adminsellerapp06", summary = "판매자 신청 거절", description = "요청\n권한: OPERATOR+\nPath: applicationId(long)\n검증: 거절 사유 필수\n처리: status REJECTED, reviewed_by·reviewed_at 기록, 감사 로그 생성", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "판매자 신청 거절 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/RejectSellerApplicationResponse"))),
        @ApiResponse(responseCode = "400", description = "REJECTION_REASON_REQUIRED"),
        @ApiResponse(responseCode = "409", description = "APPLICATION_NOT_REJECTABLE")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/admin/seller-applications/{applicationId}/rejections", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminsellerapp06(
            @PathVariable("applicationId") Long applicationId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/RejectSellerApplicationRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "adminsellerapp01", summary = "판매자 신청 목록 조회", description = "요청\n권한: OPERATOR+\nQuery: page, size, status, sellerType, countryCode, submittedFrom, submittedTo, sort", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "판매자 신청 목록 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/AdminSellerApplicationSummaryResponse"))))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/seller-applications", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminsellerapp01(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "sellerType", required = false) String sellerType,
            @RequestParam(name = "countryCode", required = false) String countryCode,
            @RequestParam(name = "submittedFrom", required = false) String submittedFrom,
            @RequestParam(name = "submittedTo", required = false) String submittedTo
    );
    @Operation(operationId = "adminsellerapp02", summary = "판매자 신청 상세 조회", description = "요청\n권한: OPERATOR+\nPath: applicationId(long)\n처리: 신청 정보, 정산 정보, 증빙 문서 메타데이터, 이전 신청 버전 조회\n보안: 계좌·연락처 마스킹, 민감정보 접근 로그 기록", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "판매자 신청 상세 조회 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/AdminSellerApplicationDetailResponse"))),
        @ApiResponse(responseCode = "404", description = "SELLER_APPLICATION_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/seller-applications/{applicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminsellerapp02(
            @PathVariable("applicationId") Long applicationId
    );
    @Operation(operationId = "adminsellerapp03", summary = "판매자 증빙 다운로드 URL 발급", description = "요청\n권한: OPERATOR+\nPath: documentId(long)\nQuery: expiresInSeconds(optional, max=300)\n처리: 짧은 만료시간의 비공개 Presigned URL 발급, 접근 감사 로그 기록", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "판매자 증빙 다운로드 URL 발급 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/PresignedUrlResponse"))),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "404", description = "DOCUMENT_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/seller-application-documents/{documentId}/download-url", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminsellerapp03(
            @PathVariable("documentId") Long documentId,
            @RequestParam(name = "expiresInSeconds", required = false) String expiresInSeconds
    );
    @Operation(operationId = "adminseller01", summary = "판매자 목록 조회", description = "요청\n권한: OPERATOR+\nQuery: page, size, status(ACTIVE|SELLING_RESTRICTED|SUSPENDED|TERMINATED), sellerType, countryCode, keyword", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "판매자 목록 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/AdminSellerSummaryResponse"))))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/sellers", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminseller01(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "sellerType", required = false) String sellerType,
            @RequestParam(name = "countryCode", required = false) String countryCode,
            @RequestParam(name = "keyword", required = false) String keyword
    );
    @Operation(operationId = "adminseller02", summary = "판매자 상세 조회", description = "요청\n권한: OPERATOR+\nPath: sellerProfileId(long)\n처리: 판매자 프로필, 회원 상태, 판매 제한 제재, 신청 이력 요약 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "판매자 상세 조회 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/AdminSellerDetailResponse"))),
        @ApiResponse(responseCode = "404", description = "SELLER_PROFILE_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/sellers/{sellerProfileId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminseller02(
            @PathVariable("sellerProfileId") Long sellerProfileId
    );
    @Operation(operationId = "adminseller03", summary = "판매자 상태 변경", description = "요청\n권한: OPERATOR+\nPath: sellerProfileId(long)\n처리: seller_profiles 상태 변경, 필요 시 SELLING 또는 ALL 제재 생성, 감사 로그 기록", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "판매자 상태 변경 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerStatusResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_STATUS_TRANSITION"),
        @ApiResponse(responseCode = "404", description = "SELLER_PROFILE_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.PATCH, path = "/api/v1/admin/sellers/{sellerProfileId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminseller03(
            @PathVariable("sellerProfileId") Long sellerProfileId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/UpdateSellerStatusRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "adminseller04", summary = "판매 한도 조정", description = "요청\n권한: OPERATOR+\nPath: sellerProfileId(long)\n검증: 0 이상의 한도, 현재 진행 거래에 영향을 주는 하향 정책 확인\n처리: seller_profiles 갱신, 감사 로그 기록", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "판매 한도 조정 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerLimitsResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_LIMIT"),
        @ApiResponse(responseCode = "404", description = "SELLER_PROFILE_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "LIMIT_BELOW_CURRENT_USAGE")
    })
    @RequestMapping(method = RequestMethod.PATCH, path = "/api/v1/admin/sellers/{sellerProfileId}/limits", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminseller04(
            @PathVariable("sellerProfileId") Long sellerProfileId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/UpdateSellerLimitsRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "admininquiry01", summary = "문의 목록 조회", description = "요청\n권한: OPERATOR+\nQuery: page, size, status, category, memberId, createdFrom, createdTo, sort", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "문의 목록 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/AdminInquirySummaryResponse"))))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/inquiries", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> admininquiry01(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "memberId", required = false) Long memberId,
            @RequestParam(name = "createdFrom", required = false) LocalDate createdFrom,
            @RequestParam(name = "createdTo", required = false) LocalDate createdTo
    );
    @Operation(operationId = "admininquiry02", summary = "문의 상세 조회", description = "요청\n권한: OPERATOR+\nPath: inquiryId(long)\n처리: 문의 본문과 답변 전체 버전 이력 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "문의 상세 조회 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/AdminInquiryDetailResponse"))),
        @ApiResponse(responseCode = "404", description = "INQUIRY_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/inquiries/{inquiryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> admininquiry02(
            @PathVariable("inquiryId") Long inquiryId
    );
    @Operation(operationId = "admininquiry03", summary = "문의 답변 등록 또는 수정", description = "요청\n권한: OPERATOR+\nPath: inquiryId(long)\n처리: 기존 최신 답변 is_current=false, 새 version 답변 생성, 문의 상태 ANSWERED, 감사 로그 기록", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "문의 답변 등록 또는 수정 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/AdminInquiryDetailResponse"))),
        @ApiResponse(responseCode = "400", description = "EMPTY_ANSWER"),
        @ApiResponse(responseCode = "404", description = "INQUIRY_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "INQUIRY_CLOSED")
    })
    @RequestMapping(method = RequestMethod.PUT, path = "/api/v1/admin/inquiries/{inquiryId}/answer", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> admininquiry03(
            @PathVariable("inquiryId") Long inquiryId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/UpsertInquiryAnswerRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "admininquiry04", summary = "문의 처리 상태 변경", description = "요청\n권한: OPERATOR+\nPath: inquiryId(long)\n또는\n{\"status\":\"CLOSED\"}\n검증: OPEN → IN_PROGRESS → ANSWERED → CLOSED 상태 전이", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "문의 처리 상태 변경 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/InquiryStatusResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_STATUS_TRANSITION"),
        @ApiResponse(responseCode = "404", description = "INQUIRY_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.PATCH, path = "/api/v1/admin/inquiries/{inquiryId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> admininquiry04(
            @PathVariable("inquiryId") Long inquiryId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/UpdateInquiryStatusRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "adminrole02", summary = "회원 역할 부여", description = "요청\n권한: SUPER_ADMIN\nPath: memberId(long)\n검증: 대상 회원·역할 존재, 중복 역할 방지\n처리: member_roles 생성, 감사 로그 기록", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "회원 역할 부여 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/MemberRoleResponse"))),
        @ApiResponse(responseCode = "404", description = "MEMBER_NOT_FOUND / ROLE_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "ROLE_ALREADY_GRANTED")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/admin/members/{memberId}/roles", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminrole02(
            @PathVariable("memberId") Long memberId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/GrantRoleRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "adminrole01", summary = "역할 목록 조회", description = "요청\n권한: SUPER_ADMIN", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "역할 목록 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/RoleResponse"))))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/roles", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminrole01();
    @Operation(operationId = "adminrole03", summary = "회원 역할 회수", description = "요청\n권한: SUPER_ADMIN\nPath: memberId(long), roleId(long)\nBody 또는 Header 정책: 회수 사유 기록 권장\n검증: 마지막 SUPER_ADMIN 회수 금지, 판매자 역할 회수 시 seller_profile 상태 정책 확인\n처리: member_roles 삭제 또는 이력 보존 정책 적용, 감사 로그 기록", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "회원 역할 회수 성공"),
        @ApiResponse(responseCode = "404", description = "MEMBER_ROLE_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "LAST_SUPER_ADMIN / ROLE_IN_USE")
    })
    @RequestMapping(method = RequestMethod.DELETE, path = "/api/v1/admin/members/{memberId}/roles/{roleId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminrole03(
            @PathVariable("memberId") Long memberId,
            @PathVariable("roleId") Long roleId
    );
    @Operation(operationId = "adminlog01", summary = "관리자 처리 이력 목록 조회", description = "요청\n권한: OPERATOR+\nQuery: page, size, adminMemberId, actionType, targetType, targetId, createdFrom, createdTo\n처리: 최신순 기본 정렬, before_data·after_data는 목록에서 요약 또는 제외", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "관리자 처리 이력 목록 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/AdminActionLogSummaryResponse"))))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/action-logs", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminlog01(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "adminMemberId", required = false) Long adminMemberId,
            @RequestParam(name = "actionType", required = false) String actionType,
            @RequestParam(name = "targetType", required = false) String targetType,
            @RequestParam(name = "targetId", required = false) Long targetId,
            @RequestParam(name = "createdFrom", required = false) LocalDate createdFrom,
            @RequestParam(name = "createdTo", required = false) LocalDate createdTo
    );
    @Operation(operationId = "adminlog02", summary = "관리자 처리 이력 상세 조회", description = "요청\n권한: OPERATOR+\nPath: adminActionLogId(long)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "관리자 처리 이력 상세 조회 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/AdminActionLogDetailResponse"))),
        @ApiResponse(responseCode = "404", description = "ADMIN_ACTION_LOG_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/admin/action-logs/{adminActionLogId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> adminlog02(
            @PathVariable("adminActionLogId") Long adminActionLogId
    );
}
