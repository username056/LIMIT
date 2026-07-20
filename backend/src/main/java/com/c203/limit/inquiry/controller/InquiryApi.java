package com.c203.limit.inquiry.controller;

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

@Tag(name = "13. 문의")
public interface InquiryApi {

    @Operation(operationId = "inquiry01", summary = "문의 등록", description = "요청\n권한: MEMBER\nBody:\n{\"category\":\"ACCOUNT\",\"title\":\"로그인 관련 문의\",\"content\":\"로그인이 반복해서 실패합니다.\"}\n검증: category(ACCOUNT|PRODUCT|ORDER|PAYMENT|DELIVERY|DISPUTE|ETC), 제목·본문 길이\n\n응답\n201 Created\n{\"data\":{\"inquiryId\":1,\"status\":\"OPEN\",\"createdAt\":\"2026-07-16T12:10:00+09:00\"}}\n오류: 400 INVALID_INPUT", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/InquiryDetailResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/inquiries", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> inquiry01(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CreateInquiryRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "inquiry02", summary = "내 문의 목록 조회", description = "요청\n권한: MEMBER\nQuery: page, size, status(optional), category(optional)\n\n응답\n200 OK\n{\"data\":{\"content\":[{\"inquiryId\":1,\"category\":\"ACCOUNT\",\"title\":\"로그인 관련 문의\",\"status\":\"ANSWERED\",\"createdAt\":\"2026-07-16T12:10:00+09:00\",\"hasCurrentAnswer\":true}],\"page\":0,\"size\":20,\"totalElements\":1}}", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/InquirySummaryResponse"))))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/inquiries", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> inquiry02(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "category", required = false) String category
    );
    @Operation(operationId = "inquiry03", summary = "내 문의 상세 조회", description = "요청\n권한: MEMBER\nPath: inquiryId(long)\n검증: 본인 작성 문의\n\n응답\n200 OK\n{\"data\":{\"inquiryId\":1,\"category\":\"ACCOUNT\",\"title\":\"로그인 관련 문의\",\"content\":\"로그인이 반복해서 실패합니다.\",\"status\":\"ANSWERED\",\"createdAt\":\"2026-07-16T12:10:00+09:00\",\"answer\":{\"content\":\"로그인 제한을 해제했습니다.\",\"answeredAt\":\"2026-07-16T13:00:00+09:00\"}}}\n오류: 403 FORBIDDEN, 404 INQUIRY_NOT_FOUND", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/InquiryDetailResponse"))),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/inquiries/{inquiryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> inquiry03(
            @PathVariable("inquiryId") Long inquiryId
    );
    @Operation(operationId = "inquiry04", summary = "문의 내용 수정", description = "요청\n권한: MEMBER\nPath: inquiryId(long)\nBody:\n{\"title\":\"로그인 제한 문의\",\"content\":\"현재도 로그인할 수 없습니다.\"}\n검증: 본인 문의이며 status가 OPEN인 경우만 수정 가능\n\n응답\n200 OK\n{\"data\":{\"inquiryId\":1,\"title\":\"로그인 제한 문의\",\"status\":\"OPEN\",\"updatedAt\":\"2026-07-16T12:20:00+09:00\"}}\n오류: 403 FORBIDDEN, 404 INQUIRY_NOT_FOUND, 409 INQUIRY_NOT_EDITABLE", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/InquiryDetailResponse"))),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.PATCH, path = "/api/v1/inquiries/{inquiryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> inquiry04(
            @PathVariable("inquiryId") Long inquiryId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/UpdateInquiryRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
}
