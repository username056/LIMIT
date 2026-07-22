package com.c203.limit.domain.admin.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminSellerSummaryResponse", description = "관리자 판매자 목록 항목")
public class AdminSellerSummaryResponse {

    @Schema(description = "판매자 프로필 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long sellerProfileId;

    @Schema(description = "회원 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long memberId;

    @Schema(description = "마스킹 이메일", example = "s***@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String memberEmail;

    @Schema(description = "상호명", example = "null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String businessName;

    @Schema(description = "판매자 유형", example = "INDIVIDUAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String sellerType;

    @Schema(description = "판매자 상태", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String sellerStatus;

    @Schema(description = "국가 코드", example = "US", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String countryCode;

    @Schema(description = "상품 등록 한도", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int productLimit;

    @Schema(description = "판매 금액 한도", example = "10000000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal salesAmountLimit;

    @Schema(description = "승인 시각", example = "2026-07-16T16:30:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime approvedAt;
}
