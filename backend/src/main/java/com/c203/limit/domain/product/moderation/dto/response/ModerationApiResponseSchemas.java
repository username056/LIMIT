package com.c203.limit.domain.product.moderation.dto.response;

import java.util.List;

import com.c203.limit.domain.product.dto.response.ProductDetailResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI에서 제네릭 응답의 실제 데이터 타입을 보존하기 위한 문서 전용 스키마입니다. */
public final class ModerationApiResponseSchemas {

    private ModerationApiResponseSchemas() {}

    @Schema(name = "ListingReportCreatedApiResponse", description = "상품 신고 접수 응답")
    public record ListingReportCreatedApiResponse(
            ListingReportCreatedResponse data, @Schema(nullable = true) Object meta) {}

    @Schema(name = "ModerationActionApiResponse", description = "판매자 운영 조치 응답")
    public record ModerationActionApiResponse(
            ModerationActionResponse data, @Schema(nullable = true) Object meta) {}

    @Schema(name = "RestorationRequestApiResponse", description = "상품 복구 신청 응답")
    public record RestorationRequestApiResponse(
            RestorationRequestResponse data, @Schema(nullable = true) Object meta) {}

    @Schema(name = "ModerationDashboardApiResponse", description = "이상 활동 대시보드 응답")
    public record ModerationDashboardApiResponse(
            ModerationDashboardResponse data, @Schema(nullable = true) Object meta) {}

    @Schema(name = "AdminListingReportApiResponse", description = "관리자 상품 신고 처리 응답")
    public record AdminListingReportApiResponse(
            AdminListingReportResponse data, @Schema(nullable = true) Object meta) {}

    @Schema(name = "AdminListingReportPageApiResponse", description = "관리자 상품 신고 목록 응답")
    public record AdminListingReportPageApiResponse(
            AdminListingReportPageData data, @Schema(nullable = true) Object meta) {}

    @Schema(name = "AdminListingReportPageData")
    public record AdminListingReportPageData(
            List<AdminListingReportResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}

    @Schema(name = "AdminRestorationRequestApiResponse", description = "관리자 복구 신청 처리 응답")
    public record AdminRestorationRequestApiResponse(
            AdminRestorationRequestResponse data, @Schema(nullable = true) Object meta) {}

    @Schema(name = "AdminRestorationRequestPageApiResponse", description = "관리자 복구 신청 목록 응답")
    public record AdminRestorationRequestPageApiResponse(
            AdminRestorationRequestPageData data, @Schema(nullable = true) Object meta) {}

    @Schema(name = "AdminRestorationRequestPageData")
    public record AdminRestorationRequestPageData(
            List<AdminRestorationRequestResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}

    @Schema(name = "ModerationRiskSignalApiResponse", description = "이상 활동 신호 처리 응답")
    public record ModerationRiskSignalApiResponse(
            ModerationRiskSignalResponse data, @Schema(nullable = true) Object meta) {}

    @Schema(name = "ModerationRiskSignalPageApiResponse", description = "이상 활동 신호 목록 응답")
    public record ModerationRiskSignalPageApiResponse(
            ModerationRiskSignalPageData data, @Schema(nullable = true) Object meta) {}

    @Schema(name = "ModerationRiskSignalPageData")
    public record ModerationRiskSignalPageData(
            List<ModerationRiskSignalResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}

    @Schema(name = "AdminModeratedProductPageApiResponse", description = "관리자 전체 상품 운영 상태 목록 응답")
    public record AdminModeratedProductPageApiResponse(
            AdminModeratedProductPageData data, @Schema(nullable = true) Object meta) {}

    @Schema(name = "AdminModeratedProductPageData")
    public record AdminModeratedProductPageData(
            List<AdminModeratedProductResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}

    @Schema(name = "AdminModeratedProductDetailApiResponse", description = "관리자 운영 상태 포함 상품 상세 응답")
    public record AdminModeratedProductDetailApiResponse(
            ProductDetailResponse data, @Schema(nullable = true) Object meta) {}
}
