package com.c203.limit.domain.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminSellerApplicationDetailResponse", description = "관리자용 판매자 신청 상세")
public class AdminSellerApplicationDetailResponse {

    @Schema(description = "신청 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long applicationId;

    @Schema(description = "신청 회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long memberId;

    @Schema(description = "신청 버전", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int applicationVersion;

    @Schema(description = "판매자 유형", example = "INDIVIDUAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String sellerType;

    @Schema(description = "신청 상태", example = "SUBMITTED", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String status;

    @Schema(description = "신청자명", example = "Woo", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String applicantName;

    @Schema(description = "마스킹 이메일", example = "s***@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String applicantEmail;

    @Schema(description = "마스킹 연락처", example = "+82-10-****-5678", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String applicantPhone;

    @Schema(description = "국가 코드", example = "US", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String countryCode;

    @Schema(description = "정산 은행명", example = "Bank A", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String settlementBankName;

    @Schema(description = "마스킹 계좌번호", example = "****7890", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String settlementAccount;

    @Schema(description = "판매 예정 카테고리", example = "SNEAKERS", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String plannedCategory;

    @Schema(description = "증빙 문서 목록", example = "[]", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<com.c203.limit.domain.seller.dto.response.SellerApplicationDocumentResponse> documents;

    @Schema(description = "제출 시각", example = "2026-07-16T13:00:00+09:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime submittedAt;

    @Schema(description = "심사 관리자 ID", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Long reviewedBy;

    @Schema(description = "심사 시각", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final OffsetDateTime reviewedAt;
}
