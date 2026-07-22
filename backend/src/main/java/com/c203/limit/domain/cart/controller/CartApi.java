package com.c203.limit.domain.cart.controller;

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

@Tag(name = "05. 장바구니")
public interface CartApi {

    @Operation(operationId = "cart02", summary = "장바구니 상품 추가", description = "요청\n권한: MEMBER\n검증: 상품 판매 가능 상태, 재고, 구매 제한 수량\n처리: 동일 상품 존재 시 정책에 따라 수량 합산", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "장바구니 상품 추가 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/CartItemResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_QUANTITY"),
        @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "PRODUCT_NOT_CARTABLE / PURCHASE_LIMIT_EXCEEDED")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/members/me/cart/items", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> cart02(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/AddCartItemRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "cart01", summary = "장바구니 조회", description = "요청\n권한: MEMBER", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "장바구니 조회 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/CartResponse")))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/members/me/cart", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> cart01();
    @Operation(operationId = "cart03", summary = "장바구니 상품 수량 수정", description = "요청\n권한: MEMBER\nPath: cartItemId(long)\n검증: 본인 장바구니 항목, 재고 및 구매 제한 수량", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "장바구니 상품 수량 수정 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/CartItemResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_QUANTITY"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "404", description = "CART_ITEM_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "PURCHASE_LIMIT_EXCEEDED")
    })
    @RequestMapping(method = RequestMethod.PATCH, path = "/api/v1/members/me/cart/items/{cartItemId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> cart03(
            @PathVariable("cartItemId") Long cartItemId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/UpdateCartItemQuantityRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "cart04", summary = "장바구니 상품 삭제", description = "요청\n권한: MEMBER\nPath: cartItemId(long)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "장바구니 상품 삭제 성공"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "404", description = "CART_ITEM_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.DELETE, path = "/api/v1/members/me/cart/items/{cartItemId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> cart04(
            @PathVariable("cartItemId") Long cartItemId
    );
    @Operation(operationId = "cart05", summary = "장바구니 전체 비우기", description = "요청\n권한: MEMBER", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "장바구니 전체 비우기 성공")
    })
    @RequestMapping(method = RequestMethod.DELETE, path = "/api/v1/members/me/cart/items", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> cart05();
}
