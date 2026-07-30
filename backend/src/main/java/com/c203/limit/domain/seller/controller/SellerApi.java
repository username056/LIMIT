package com.c203.limit.domain.seller.controller;

import com.c203.limit.domain.seller.dto.request.CreateSellerRequest;
import com.c203.limit.domain.seller.dto.response.PublicSellerProfileResponse;
import com.c203.limit.domain.seller.dto.response.SellerProfileResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "07. 판매자", description = "회원의 즉시 판매자 등록과 판매자 프로필 API")
public interface SellerApi {

    @Operation(
            operationId = "seller01",
            summary = "판매자 등록",
            description = "활성·이메일 인증 회원을 ACTIVE 판매자로 즉시 등록합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "판매자 즉시 등록 성공")
    @PostMapping("/api/v1/sellers")
    ResponseEntity<ApiResponse<SellerProfileResponse>> register(
            @Valid @RequestBody CreateSellerRequest request);

    @Operation(
            operationId = "seller02",
            summary = "내 판매자 프로필 조회",
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "내 판매자 프로필 조회 성공")
    @GetMapping("/api/v1/sellers/me")
    ResponseEntity<ApiResponse<SellerProfileResponse>> profile();

    @Operation(
            operationId = "seller03",
            summary = "판매자 공개 프로필 조회",
            description =
                    "상품 상세에서 판매자를 확인할 때 씁니다. 닉네임·개인·사업자 구분·등록 시각·판매 중 상품 수만"
                            + " 반환하며 정산 계좌와 사업자 상호는 포함하지 않습니다."
                            + " 판매자 등록 행이 없는 회원도 상품을 가질 수 있어(listing.seller_id는 FK 없는"
                            + " 회원 ID), 그 경우 sellerType과 joinedAt은 null로 반환합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "판매자 공개 프로필 조회 성공")
    @GetMapping("/api/v1/sellers/{sellerId}")
    ResponseEntity<ApiResponse<PublicSellerProfileResponse>> publicProfile(
            @Parameter(
                            description =
                                    "판매자의 회원 ID. ProductDetailResponse.sellerId와 같은 값이며"
                                            + " seller 테이블의 PK(seller_id)가 아니다.",
                            example = "20")
                    @PathVariable
                    Long sellerId);
}
