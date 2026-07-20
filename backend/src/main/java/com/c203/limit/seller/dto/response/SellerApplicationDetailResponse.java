package com.c203.limit.seller.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "SellerApplicationDetailResponse", description = "본인 판매자 신청 상세")
public class SellerApplicationDetailResponse {

    @Schema(description = "신청 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long applicationId;

    @Schema(description = "신청 버전", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int applicationVersion;

    @Schema(description = "판매자 유형", example = "INDIVIDUAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String sellerType;

    @Schema(description = "신청 상태", example = "DRAFT", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "신청자명", example = "Woo", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String applicantName;

    @Schema(description = "신청 이메일", example = "seller@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String applicantEmail;

    @Schema(description = "마스킹된 연락처", example = "82-10-****-5678", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String applicantPhone;

    @Schema(description = "국가 코드", example = "US", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String countryCode;

    @Schema(description = "상호명", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String businessName;

    @Schema(description = "판매 예정 카테고리", example = "SNEAKERS", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String plannedCategory;

    @Schema(description = "증빙 문서 목록", example = "[]", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<SellerApplicationDocumentResponse> documents;

    @Schema(description = "거절 사유", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String rejectionReason;

    @Schema(description = "제출 시각", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime submittedAt;

    @Schema(description = "심사 시각", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime reviewedAt;

    @Schema(description = "생성 시각", example = "2026-07-16T12:30:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime createdAt;

    @Schema(description = "수정 시각", example = "2026-07-16T12:45:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime updatedAt;
}
