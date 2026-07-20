package com.c203.limit.domain.seller.controller;

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

@Tag(name = "12. 판매자")
public interface SellerApi {

    @Operation(operationId = "sellerapp01", summary = "판매자 신청서 초안 생성", description = "요청\n권한: MEMBER\nBody:\n{\"sellerType\":\"INDIVIDUAL\"}\n처리: 회원의 다음 applicationVersion으로 DRAFT 신청서 생성\n\n응답\n201 Created\n{\"data\":{\"applicationId\":1,\"applicationVersion\":1,\"sellerType\":\"INDIVIDUAL\",\"status\":\"DRAFT\",\"createdAt\":\"2026-07-16T12:30:00+09:00\"}}\n오류: 409 ACTIVE_APPLICATION_EXISTS, 409 ALREADY_SELLER", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerApplicationSummaryResponse"))),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/seller-applications", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sellerapp01(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CreateSellerApplicationRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "sellerapp02", summary = "내 판매자 신청 목록 조회", description = "요청\n권한: MEMBER\nQuery: page, size\n\n응답\n200 OK\n{\"data\":{\"content\":[{\"applicationId\":1,\"applicationVersion\":1,\"sellerType\":\"INDIVIDUAL\",\"status\":\"UNDER_REVIEW\",\"submittedAt\":\"2026-07-16T13:00:00+09:00\",\"reviewedAt\":null}],\"totalElements\":1}}", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/SellerApplicationSummaryResponse"))))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/seller-applications", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sellerapp02(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    );
    @Operation(operationId = "sellerapp03", summary = "내 판매자 신청 상세 조회", description = "요청\n권한: MEMBER\nPath: applicationId(long)\n검증: 본인 신청서\n\n응답\n200 OK\n{\"data\":{\"applicationId\":1,\"applicationVersion\":1,\"sellerType\":\"INDIVIDUAL\",\"status\":\"DRAFT\",\"applicantName\":\"Woo\",\"applicantEmail\":\"seller@example.com\",\"applicantPhone\":\"82-10-****-5678\",\"countryCode\":\"US\",\"businessName\":null,\"plannedCategory\":\"SNEAKERS\",\"documents\":[{\"documentId\":1,\"documentType\":\"PASSPORT\",\"originalFilename\":\"passport.pdf\",\"uploadedAt\":\"2026-07-16T12:40:00+09:00\"}]}}\n오류: 403 FORBIDDEN, 404 SELLER_APPLICATION_NOT_FOUND", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerApplicationDetailResponse"))),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/seller-applications/{applicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sellerapp03(
            @PathVariable("applicationId") Long applicationId
    );
    @Operation(operationId = "sellerapp04", summary = "판매자 신청서 수정", description = "요청\n권한: MEMBER\nPath: applicationId(long)\nBody:\n{\"applicantName\":\"Woo\",\"applicantEmail\":\"seller@example.com\",\"applicantPhone\":\"+821012345678\",\"countryCode\":\"US\",\"businessName\":null,\"businessNumber\":null,\"businessAddress\":\"New York ...\",\"settlementBankName\":\"Bank A\",\"settlementAccount\":\"1234567890\",\"settlementAccountHolder\":\"Woo\",\"plannedCategory\":\"SNEAKERS\",\"termsAgreed\":true}\n검증: 본인의 DRAFT 또는 REJECTED 신청서\n\n응답\n200 OK\n{\"data\":{\"applicationId\":1,\"status\":\"DRAFT\",\"updatedAt\":\"2026-07-16T12:45:00+09:00\"}}\n오류: 400 INVALID_INPUT, 403 FORBIDDEN, 409 APPLICATION_NOT_EDITABLE", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerApplicationDetailResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.PATCH, path = "/api/v1/seller-applications/{applicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sellerapp04(
            @PathVariable("applicationId") Long applicationId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/UpdateSellerApplicationRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "sellerapp05", summary = "판매자 증빙 문서 등록", description = "요청\n권한: MEMBER\nContent-Type: multipart/form-data\nPath: applicationId(long)\nParts: documentType(PASSPORT|IDENTITY_CARD|BUSINESS_LICENSE|BANK_ACCOUNT_PROOF), file(binary)\n검증: 본인 신청서, 허용 확장자·MIME·용량·악성 파일\n\n응답\n201 Created\n{\"data\":{\"documentId\":1,\"applicationId\":1,\"documentType\":\"PASSPORT\",\"originalFilename\":\"passport.pdf\",\"contentType\":\"application/pdf\",\"fileSize\":102400,\"uploadedAt\":\"2026-07-16T12:50:00+09:00\"}}\n오류: 400 INVALID_FILE, 403 FORBIDDEN, 409 APPLICATION_NOT_EDITABLE", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerApplicationDocumentResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/seller-applications/{applicationId}/documents", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Void> sellerapp05(
            @PathVariable("applicationId") Long applicationId,
            @RequestPart(name = "files", required = false) MultipartFile[] files
    );
    @Operation(operationId = "sellerapp06", summary = "판매자 증빙 문서 삭제", description = "요청\n권한: MEMBER\nPath: applicationId(long), documentId(long)\n검증: 본인 신청서, DRAFT 또는 REJECTED 상태\n\n응답\n204 No Content\n오류: 403 FORBIDDEN, 404 DOCUMENT_NOT_FOUND, 409 APPLICATION_NOT_EDITABLE", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "명세 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.DELETE, path = "/api/v1/seller-applications/{applicationId}/documents/{documentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sellerapp06(
            @PathVariable("applicationId") Long applicationId,
            @PathVariable("documentId") Long documentId
    );
    @Operation(operationId = "sellerapp07", summary = "판매자 신청서 제출", description = "요청\n권한: MEMBER\nPath: applicationId(long)\nBody: 없음\n검증: 필수 신청 정보·증빙 문서·약관 동의 완료\n처리: status SUBMITTED, submitted_at 기록\n\n응답\n200 OK\n{\"data\":{\"applicationId\":1,\"status\":\"SUBMITTED\",\"submittedAt\":\"2026-07-16T13:00:00+09:00\"}}\n오류: 400 APPLICATION_INCOMPLETE, 403 FORBIDDEN, 409 APPLICATION_NOT_SUBMITTABLE", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerApplicationStatusResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/seller-applications/{applicationId}/submissions", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sellerapp07(
            @PathVariable("applicationId") Long applicationId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(type = "object"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "sellerapp08", summary = "판매자 신청 취소", description = "요청\n권한: MEMBER\nPath: applicationId(long)\nBody:\n{\"reason\":\"신청 정보 재작성\"}\n검증: 본인의 DRAFT, SUBMITTED, UNDER_REVIEW 신청서 중 정책상 취소 가능 상태\n\n응답\n200 OK\n{\"data\":{\"applicationId\":1,\"status\":\"CANCELED\",\"updatedAt\":\"2026-07-16T13:05:00+09:00\"}}\n오류: 403 FORBIDDEN, 409 APPLICATION_NOT_CANCELABLE", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerApplicationStatusResponse"))),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/seller-applications/{applicationId}/cancellations", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sellerapp08(
            @PathVariable("applicationId") Long applicationId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CancelSellerApplicationRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "seller01", summary = "내 판매자 프로필 조회", description = "요청\n권한: SELLER\n\n응답\n200 OK\n{\"data\":{\"sellerProfileId\":1,\"memberId\":1,\"sellerType\":\"INDIVIDUAL\",\"sellerStatus\":\"ACTIVE\",\"countryCode\":\"US\",\"businessName\":null,\"productLimit\":10,\"salesAmountLimit\":10000000,\"approvedAt\":\"2026-07-20T10:00:00+09:00\"}}\n오류: 403 SELLER_ROLE_REQUIRED, 404 SELLER_PROFILE_NOT_FOUND", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerProfileResponse"))),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/sellers/me", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> seller01();
}
