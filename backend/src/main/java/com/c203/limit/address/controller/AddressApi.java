package com.c203.limit.address.controller;

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

@Tag(name = "03. 배송지")
public interface AddressApi {

    @Operation(operationId = "address01", summary = "배송지 목록 조회", description = "요청\n권한: MEMBER\nQuery: includeDeleted(boolean, default=false)\n\n응답\n200 OK\n{\"data\":[{\"addressId\":1,\"addressName\":\"집\",\"recipientName\":\"우성현\",\"recipientPhone\":\"010****5678\",\"countryCode\":\"KR\",\"postalCode\":\"06236\",\"state\":\"서울특별시\",\"city\":\"강남구\",\"addressLine1\":\"테헤란로 123\",\"addressLine2\":\"101동 1001호\",\"isDefault\":true}]}", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/AddressResponse"))))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/members/me/addresses", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> address01(
            @RequestParam(name = "includeDeleted", required = false) Boolean includeDeleted
    );
    @Operation(operationId = "address02", summary = "배송지 등록", description = "요청\n권한: MEMBER\nBody:\n{\"addressName\":\"집\",\"recipientName\":\"우성현\",\"recipientPhone\":\"01012345678\",\"countryCode\":\"KR\",\"postalCode\":\"06236\",\"state\":\"서울특별시\",\"city\":\"강남구\",\"addressLine1\":\"테헤란로 123\",\"addressLine2\":\"101동 1001호\",\"isDefault\":true}\n검증: 국가별 필수 주소 형식, 기본 배송지 단일 보장\n\n응답\n201 Created\n{\"data\":{\"addressId\":1,\"isDefault\":true,\"createdAt\":\"2026-07-16T11:20:00+09:00\"}}\n오류: 400 INVALID_ADDRESS, 409 ADDRESS_LIMIT_EXCEEDED", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/AddressResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/members/me/addresses", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> address02(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CreateAddressRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "address03", summary = "배송지 수정", description = "요청\n권한: MEMBER\nPath: addressId(long)\nBody: 등록 API와 동일한 필드 중 변경할 값\n검증: 본인 소유 배송지\n\n응답\n200 OK\n{\"data\":{\"addressId\":1,\"addressName\":\"회사\",\"isDefault\":false,\"updatedAt\":\"2026-07-16T11:25:00+09:00\"}}\n오류: 403 FORBIDDEN, 404 ADDRESS_NOT_FOUND, 400 INVALID_ADDRESS", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/AddressResponse"))),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.PATCH, path = "/api/v1/members/me/addresses/{addressId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> address03(
            @PathVariable("addressId") Long addressId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/UpdateAddressRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "address04", summary = "배송지 삭제", description = "요청\n권한: MEMBER\nPath: addressId(long)\n처리: deleted_at 기록을 이용한 Soft Delete\n검증: 진행 중 주문에서 사용 중인 주소 정책 확인\n\n응답\n204 No Content\n오류: 403 FORBIDDEN, 404 ADDRESS_NOT_FOUND, 409 ADDRESS_IN_USE", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "명세 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.DELETE, path = "/api/v1/members/me/addresses/{addressId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> address04(
            @PathVariable("addressId") Long addressId
    );
    @Operation(operationId = "address05", summary = "기본 배송지 설정", description = "요청\n권한: MEMBER\nPath: addressId(long)\n처리: 기존 기본 배송지 해제 후 대상 배송지를 기본값으로 설정\n\n응답\n200 OK\n{\"data\":{\"addressId\":1,\"isDefault\":true}}\n오류: 403 FORBIDDEN, 404 ADDRESS_NOT_FOUND", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/AddressResponse"))),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.PUT, path = "/api/v1/members/me/default-addresses/{addressId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> address05(
            @PathVariable("addressId") Long addressId
    );
}
