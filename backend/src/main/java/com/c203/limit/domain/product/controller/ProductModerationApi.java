package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.product.moderation.dto.request.CreateListingReportRequest;
import com.c203.limit.domain.product.moderation.dto.request.CreateRestorationRequest;
import com.c203.limit.domain.product.moderation.dto.response.ListingReportCreatedResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationActionResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationApiResponseSchemas.ListingReportCreatedApiResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationApiResponseSchemas.ModerationActionApiResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationApiResponseSchemas.RestorationRequestApiResponse;
import com.c203.limit.domain.product.moderation.dto.response.RestorationRequestResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

public interface ProductModerationApi {
    @Operation(summary = "상품 신고", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "신고 접수",
                content = @Content(schema = @Schema(implementation = ListingReportCreatedApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "본인 상품 신고 또는 입력값 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "같은 회원의 중복 신고"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "공개 상품을 찾을 수 없음")
    })
    @PostMapping("/api/v1/products/{productId}/reports")
    ResponseEntity<ApiResponse<ListingReportCreatedResponse>> report(
            @PathVariable Long productId,
            @Valid @RequestBody CreateListingReportRequest request);

    @Operation(summary = "상품 경고 확인", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "경고 확인 완료",
                content = @Content(schema = @Schema(implementation = ModerationActionApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "경고 확인 상태가 아님")
    })
    @PostMapping("/api/v1/products/{productId}/moderation-warning-acknowledgements")
    ResponseEntity<ApiResponse<ModerationActionResponse>> acknowledgeWarning(
            @PathVariable Long productId);

    @Operation(summary = "판매 중지 상품 복구 신청", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "복구 신청 접수",
                content = @Content(schema = @Schema(implementation = RestorationRequestApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "판매 중지 상태가 아니거나 이미 심사 중")
    })
    @PostMapping("/api/v1/products/{productId}/restoration-requests")
    ResponseEntity<ApiResponse<RestorationRequestResponse>> requestRestoration(
            @PathVariable Long productId,
            @Valid @RequestBody CreateRestorationRequest request);
}
