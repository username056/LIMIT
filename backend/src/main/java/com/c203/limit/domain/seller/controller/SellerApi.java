package com.c203.limit.domain.seller.controller;

import com.c203.limit.domain.seller.dto.request.CreateSellerRequest;
import com.c203.limit.domain.seller.dto.response.SellerProfileResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
