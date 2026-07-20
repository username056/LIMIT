package com.c203.limit.domain.favorite.controller;

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

@Tag(name = "06. 관심 상품")
public interface FavoriteApi {

    @Operation(operationId = "favorite01", summary = "관심 상품 목록 조회", description = "요청\n권한: MEMBER\nQuery: page(int, default=0), size(int, default=20)\n\n응답\n200 OK\n{\"data\":{\"content\":[{\"favoriteProductId\":1,\"productId\":101,\"productName\":\"Limited Sneaker\",\"thumbnailUrl\":\"...\",\"saleStatus\":\"UPCOMING\",\"createdAt\":\"2026-07-10T10:00:00+09:00\"}],\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1}}", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/FavoriteProductResponse"))))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/members/me/favorite-products", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> favorite01(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    );
    @Operation(operationId = "favorite02", summary = "관심 상품 등록", description = "요청\n권한: MEMBER\nPath: productId(long)\n검증: 상품 존재 여부, 회원-상품 중복 등록 방지\n\n응답\n201 Created\n{\"data\":{\"favoriteProductId\":1,\"productId\":101,\"createdAt\":\"2026-07-16T11:50:00+09:00\"}}\n오류: 404 PRODUCT_NOT_FOUND, 409 FAVORITE_ALREADY_EXISTS", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/FavoriteProductResponse"))),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/members/me/favorite-products/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> favorite02(
            @PathVariable("productId") Long productId
    );
    @Operation(operationId = "favorite03", summary = "관심 상품 해제", description = "요청\n권한: MEMBER\nPath: productId(long)\n\n응답\n204 No Content\n오류: 404 FAVORITE_NOT_FOUND", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "명세 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.DELETE, path = "/api/v1/members/me/favorite-products/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> favorite03(
            @PathVariable("productId") Long productId
    );
}
