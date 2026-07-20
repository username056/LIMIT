package com.c203.limit.seller.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "UpdateSellerApplicationRequest", description = "판매자 신청서 수정 요청")
public class UpdateSellerApplicationRequest {

    @Schema(description = "신청자 또는 대표자명", example = "Woo", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String applicantName;

    @Schema(description = "심사 연락 이메일", example = "seller@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String applicantEmail;

    @Schema(description = "심사 연락처", example = "+821012345678", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String applicantPhone;

    @Schema(description = "판매 국가 코드", example = "US", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String countryCode;

    @Schema(description = "기업 상호명", example = "Open Shop", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String businessName;

    @Schema(description = "현지 사업자 식별번호", example = "US-1234", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String businessNumber;

    @Schema(description = "사업장 주소", example = "New York ...", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String businessAddress;

    @Schema(description = "정산 은행명", example = "Bank A", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String settlementBankName;

    @Schema(description = "암호화 저장할 정산 계좌", example = "1234567890", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String settlementAccount;

    @Schema(description = "예금주", example = "Woo", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String settlementAccountHolder;

    @Schema(description = "판매 예정 카테고리", example = "SNEAKERS", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String plannedCategory;

    @Schema(description = "판매자 약관 동의 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean isTermsAgreed;
}
