package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.product.dto.response.FavoriteProductApiResponse;
import com.c203.limit.domain.product.dto.response.FavoriteProductPageApiResponse;
import com.c203.limit.domain.product.dto.response.FavoriteProductResponse;
import com.c203.limit.domain.product.dto.response.FavoriteStatusApiResponse;
import com.c203.limit.domain.product.dto.response.FavoriteStatusResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "06. 관심 상품", description = "회원 관심 상품 등록·조회·해제 API")
@SecurityRequirement(name = "bearerAuth")
public interface WishlistApi {

    @Operation(operationId = "wishlist01", summary = "관심 상품 등록", description = "같은 상품을 다시 등록하면 기존 관심 상품을 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "관심 상품 등록 성공",
                content = @Content(schema = @Schema(implementation = FavoriteProductApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "이미 등록된 관심 상품",
                content = @Content(schema = @Schema(implementation = FavoriteProductApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "LISTING_NOT_FOUND")
    })
    @PostMapping(path = "/api/v1/products/{productId}/favorites", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<FavoriteProductResponse>> addFavorite(@PathVariable Long productId);

    @Operation(operationId = "wishlist02", summary = "내 관심 상품 목록 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "관심 상품 목록 조회 성공",
            content = @Content(schema = @Schema(implementation = FavoriteProductPageApiResponse.class)))
    @GetMapping(path = "/api/v1/members/me/favorites", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<List<FavoriteProductResponse>>> getMyFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    @Operation(operationId = "wishlist02a", summary = "내 관심 상품 등록 상태 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "관심 상품 상태 조회 성공",
            content = @Content(schema = @Schema(implementation = FavoriteStatusApiResponse.class)))
    @GetMapping(path = "/api/v1/products/{productId}/favorites/me", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<FavoriteStatusResponse>> getFavoriteStatus(
            @PathVariable Long productId);

    @Operation(operationId = "wishlist03", summary = "관심 상품 해제", description = "이미 해제된 상품에도 동일하게 성공 응답합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "관심 상품 해제 성공")
    @DeleteMapping(path = "/api/v1/products/{productId}/favorites")
    ResponseEntity<Void> removeFavorite(@PathVariable Long productId);
}
