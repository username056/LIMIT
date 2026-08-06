package com.c203.limit.domain.admin.controller;

import com.c203.limit.domain.product.dto.response.ProductDetailResponse;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.moderation.dto.request.AdminModerationDecisionRequest;
import com.c203.limit.domain.product.moderation.dto.request.AdminRestorationDecisionRequest;
import com.c203.limit.domain.product.moderation.dto.request.ResolveRiskSignalRequest;
import com.c203.limit.domain.product.moderation.dto.response.AdminListingReportResponse;
import com.c203.limit.domain.product.moderation.dto.response.AdminModeratedProductResponse;
import com.c203.limit.domain.product.moderation.dto.response.AdminRestorationRequestResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationApiResponseSchemas.AdminListingReportApiResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationApiResponseSchemas.AdminListingReportPageApiResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationApiResponseSchemas.AdminModeratedProductDetailApiResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationApiResponseSchemas.AdminModeratedProductPageApiResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationApiResponseSchemas.AdminRestorationRequestApiResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationApiResponseSchemas.AdminRestorationRequestPageApiResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationApiResponseSchemas.ModerationDashboardApiResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationApiResponseSchemas.ModerationRiskSignalApiResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationApiResponseSchemas.ModerationRiskSignalPageApiResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationDashboardResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationRiskSignalResponse;
import com.c203.limit.domain.product.moderation.entity.ListingModerationStatus;
import com.c203.limit.domain.product.moderation.entity.ListingReportStatus;
import com.c203.limit.domain.product.moderation.entity.ModerationRiskStatus;
import com.c203.limit.domain.product.moderation.entity.ModerationRiskType;
import com.c203.limit.domain.product.moderation.entity.RestorationRequestStatus;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/v1/admin")
@SecurityRequirement(name = "bearerAuth")
public interface AdminModerationApi {
    @Operation(summary = "신고·이상 활동 대시보드")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "대시보드 조회 성공",
            content =
                    @Content(
                            schema =
                                    @Schema(implementation = ModerationDashboardApiResponse.class)))
    @GetMapping("/moderation/dashboard")
    ResponseEntity<ApiResponse<ModerationDashboardResponse>> dashboard();

    @Operation(summary = "상품 신고 목록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "신고 목록 조회 성공",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    AdminListingReportPageApiResponse.class)))
    @GetMapping("/reports")
    ResponseEntity<ApiResponse<PageResponse<AdminListingReportResponse>>> reports(
            @RequestParam(required = false) ListingReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "상품 신고 처리")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "신고 처리 성공",
            content =
                    @Content(
                            schema = @Schema(implementation = AdminListingReportApiResponse.class)))
    @PostMapping("/reports/{reportId}/decisions")
    ResponseEntity<ApiResponse<AdminListingReportResponse>> decideReport(
            @PathVariable Long reportId,
            @Valid @RequestBody AdminModerationDecisionRequest request);

    @Operation(summary = "상품 복구 신청 목록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "복구 신청 목록 조회 성공",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    AdminRestorationRequestPageApiResponse.class)))
    @GetMapping("/restoration-requests")
    ResponseEntity<ApiResponse<PageResponse<AdminRestorationRequestResponse>>> restorationRequests(
            @RequestParam(required = false) RestorationRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "상품 복구 신청 심사")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "복구 신청 심사 성공",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    AdminRestorationRequestApiResponse.class)))
    @PostMapping("/restoration-requests/{requestId}/decisions")
    ResponseEntity<ApiResponse<AdminRestorationRequestResponse>> decideRestoration(
            @PathVariable Long requestId,
            @Valid @RequestBody AdminRestorationDecisionRequest request);

    @Operation(summary = "이상 활동 신호 목록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "이상 활동 신호 목록 조회 성공",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    ModerationRiskSignalPageApiResponse.class)))
    @GetMapping("/risk-signals")
    ResponseEntity<ApiResponse<PageResponse<ModerationRiskSignalResponse>>> riskSignals(
            @RequestParam(required = false) ModerationRiskStatus status,
            @RequestParam(required = false) ModerationRiskType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "이상 활동 신호 검토 완료")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "이상 활동 신호 검토 완료",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    ModerationRiskSignalApiResponse.class)))
    @PatchMapping("/risk-signals/{signalId}")
    ResponseEntity<ApiResponse<ModerationRiskSignalResponse>> resolveRiskSignal(
            @PathVariable Long signalId, @Valid @RequestBody ResolveRiskSignalRequest request);

    @Operation(summary = "전체 상품 운영 상태 목록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "전체 상품 운영 상태 목록 조회 성공",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    AdminModeratedProductPageApiResponse.class)))
    @GetMapping("/products")
    ResponseEntity<ApiResponse<PageResponse<AdminModeratedProductResponse>>> products(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) ListingStatus lifecycleStatus,
            @RequestParam(required = false) ListingModerationStatus moderationStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "운영 상태와 무관한 관리자 상품 상세 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "관리자 상품 상세 조회 성공",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation =
                                                    AdminModeratedProductDetailApiResponse.class)))
    @GetMapping("/products/{productId}")
    ResponseEntity<ApiResponse<ProductDetailResponse>> productDetail(@PathVariable Long productId);
}
