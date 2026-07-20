package com.c203.limit.cart.controller;

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

    @Operation(operationId = "cart01", summary = "장바구니 조회", description = "요청\n권한: MEMBER\n\n응답\n200 OK\n{\"data\":{\"cartId\":1,\"items\":[{\"cartItemId\":11,\"productId\":101,\"productName\":\"Limited Sneaker\",\"quantity\":1,\"unitPrice\":199000,\"saleStatus\":\"ON_SALE\",\"stockAvailable\":true}],\"totalItemCount\":1,\"totalAmount\":199000,\"updatedAt\":\"2026-07-16T11:55:00+09:00\"}}", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/CartResponse")))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/members/me/cart", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> cart01();
    @Operation(operationId = "cart02", summary = "장바구니 상품 추가", description = "요청\n권한: MEMBER\nBody:\n{\"productId\":101,\"quantity\":1}\n검증: 상품 판매 가능 상태, 재고, 구매 제한 수량\n처리: 동일 상품 존재 시 정책에 따라 수량 합산\n\n응답\n201 Created\n{\"data\":{\"cartItemId\":11,\"productId\":101,\"quantity\":1,\"createdAt\":\"2026-07-16T12:00:00+09:00\"}}\n오류: 400 INVALID_QUANTITY, 404 PRODUCT_NOT_FOUND, 409 PRODUCT_NOT_CARTABLE, 409 PURCHASE_LIMIT_EXCEEDED", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/CartItemResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/members/me/cart/items", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> cart02(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/AddCartItemRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "cart03", summary = "장바구니 상품 수량 수정", description = "요청\n권한: MEMBER\nPath: cartItemId(long)\nBody:\n{\"quantity\":2}\n검증: 본인 장바구니 항목, 재고 및 구매 제한 수량\n\n응답\n200 OK\n{\"data\":{\"cartItemId\":11,\"quantity\":2,\"updatedAt\":\"2026-07-16T12:05:00+09:00\"}}\n오류: 400 INVALID_QUANTITY, 403 FORBIDDEN, 404 CART_ITEM_NOT_FOUND, 409 PURCHASE_LIMIT_EXCEEDED", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/CartItemResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.PATCH, path = "/api/v1/members/me/cart/items/{cartItemId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> cart03(
            @PathVariable("cartItemId") Long cartItemId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/UpdateCartItemQuantityRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "cart04", summary = "장바구니 상품 삭제", description = "요청\n권한: MEMBER\nPath: cartItemId(long)\n\n응답\n204 No Content\n오류: 403 FORBIDDEN, 404 CART_ITEM_NOT_FOUND", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "명세 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.DELETE, path = "/api/v1/members/me/cart/items/{cartItemId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> cart04(
            @PathVariable("cartItemId") Long cartItemId
    );
    @Operation(operationId = "cart05", summary = "장바구니 전체 비우기", description = "요청\n권한: MEMBER\n\n응답\n204 No Content", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "명세 응답")
    })
    @RequestMapping(method = RequestMethod.DELETE, path = "/api/v1/members/me/cart/items", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> cart05();
}
