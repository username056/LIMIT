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

    @Operation(operationId = "favorite02", summary = "관심 상품 등록", description = "요청\n권한: MEMBER\nPath: productId(long)\n검증: 상품 존재 여부, 회원-상품 중복 등록 방지", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "관심 상품 등록 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/FavoriteProductResponse"))),
        @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "FAVORITE_ALREADY_EXISTS")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/members/me/favorite-products/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> favorite02(
            @PathVariable("productId") Long productId
    );
    @Operation(operationId = "favorite01", summary = "관심 상품 목록 조회", description = "요청\n권한: MEMBER\nQuery: page(int, default=0), size(int, default=20)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "관심 상품 목록 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/FavoriteProductResponse"))))
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/members/me/favorite-products", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> favorite01(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    );
    @Operation(operationId = "favorite03", summary = "관심 상품 해제", description = "요청\n권한: MEMBER\nPath: productId(long)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "관심 상품 해제 성공"),
        @ApiResponse(responseCode = "404", description = "FAVORITE_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.DELETE, path = "/api/v1/members/me/favorite-products/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> favorite03(
            @PathVariable("productId") Long productId
    );
}
