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

    @Operation(operationId = "sellerapp01", summary = "판매자 신청서 초안 생성", description = "요청\n권한: MEMBER\n처리: 회원의 다음 applicationVersion으로 DRAFT 신청서 생성", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "판매자 신청서 초안 생성 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerApplicationSummaryResponse"))),
        @ApiResponse(responseCode = "409", description = "ACTIVE_APPLICATION_EXISTS / ALREADY_SELLER")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/seller-applications", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sellerapp01(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CreateSellerApplicationRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "sellerapp05", summary = "판매자 증빙 문서 등록", description = "요청\n권한: MEMBER\nContent-Type: multipart/form-data\nPath: applicationId(long)\nParts: documentType(PASSPORT|IDENTITY_CARD|BUSINESS_LICENSE|BANK_ACCOUNT_PROOF), file(binary)\n검증: 본인 신청서, 허용 확장자·MIME·용량·악성 파일", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "판매자 증빙 문서 등록 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerApplicationDocumentResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_FILE"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "409", description = "APPLICATION_NOT_EDITABLE")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/seller-applications/{applicationId}/documents", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Void> sellerapp05(
            @PathVariable("applicationId") Long applicationId,
            @RequestPart(name = "files", required = false) MultipartFile[] files
    );
    @Operation(operationId = "sellerapp07", summary = "판매자 신청서 제출", description = "요청\n권한: MEMBER\nPath: applicationId(long)\nBody: 없음\n검증: 필수 신청 정보·증빙 문서·약관 동의 완료\n처리: status SUBMITTED, submitted_at 기록", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "판매자 신청서 제출 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerApplicationStatusResponse"))),
        @ApiResponse(responseCode = "400", description = "APPLICATION_INCOMPLETE"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "409", description = "APPLICATION_NOT_SUBMITTABLE")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/seller-applications/{applicationId}/submissions", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sellerapp07(
            @PathVariable("applicationId") Long applicationId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(type = "object"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "sellerapp08", summary = "판매자 신청 취소", description = "요청\n권한: MEMBER\nPath: applicationId(long)\n검증: 본인의 DRAFT, SUBMITTED, UNDER_REVIEW 신청서 중 정책상 취소 가능 상태", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "판매자 신청 취소 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerApplicationStatusResponse"))),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "409", description = "APPLICATION_NOT_CANCELABLE")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/seller-applications/{applicationId}/cancellations", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sellerapp08(
            @PathVariable("applicationId") Long applicationId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CancelSellerApplicationRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "sellerapp02", summary = "내 판매자 신청 목록 조회", description = "요청\n권한: MEMBER\nQuery: page, size", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "내 판매자 신청 목록 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/SellerApplicationSummaryResponse"))))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/seller-applications", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sellerapp02(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    );
    @Operation(operationId = "sellerapp03", summary = "내 판매자 신청 상세 조회", description = "요청\n권한: MEMBER\nPath: applicationId(long)\n검증: 본인 신청서", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "내 판매자 신청 상세 조회 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerApplicationDetailResponse"))),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "404", description = "SELLER_APPLICATION_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/seller-applications/{applicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sellerapp03(
            @PathVariable("applicationId") Long applicationId
    );
    @Operation(operationId = "sellerapp04", summary = "판매자 신청서 수정", description = "요청\n권한: MEMBER\nPath: applicationId(long)\n검증: 본인의 DRAFT 또는 REJECTED 신청서", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "판매자 신청서 수정 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerApplicationDetailResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_INPUT"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "409", description = "APPLICATION_NOT_EDITABLE")
    })
    @RequestMapping(method = RequestMethod.PATCH, path = "/api/v1/seller-applications/{applicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sellerapp04(
            @PathVariable("applicationId") Long applicationId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/UpdateSellerApplicationRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "sellerapp06", summary = "판매자 증빙 문서 삭제", description = "요청\n권한: MEMBER\nPath: applicationId(long), documentId(long)\n검증: 본인 신청서, DRAFT 또는 REJECTED 상태", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "판매자 증빙 문서 삭제 성공"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "404", description = "DOCUMENT_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "APPLICATION_NOT_EDITABLE")
    })
    @RequestMapping(method = RequestMethod.DELETE, path = "/api/v1/seller-applications/{applicationId}/documents/{documentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sellerapp06(
            @PathVariable("applicationId") Long applicationId,
            @PathVariable("documentId") Long documentId
    );
    @Operation(operationId = "seller01", summary = "내 판매자 프로필 조회", description = "요청\n권한: SELLER", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "내 판매자 프로필 조회 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/SellerProfileResponse"))),
        @ApiResponse(responseCode = "403", description = "SELLER_ROLE_REQUIRED"),
        @ApiResponse(responseCode = "404", description = "SELLER_PROFILE_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/sellers/me", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> seller01();
}
