package com.c203.limit.domain.address.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "UpdateAddressRequest", description = "배송지 수정 요청")
public class UpdateAddressRequest {

    @Schema(description = "배송지 구분명", example = "집", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String addressName;

    @Schema(description = "수령인 이름", example = "우성현", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String recipientName;

    @Schema(description = "수령인 연락처", example = "01012345678", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String recipientPhone;

    @Schema(description = "ISO 3166-1 alpha-2 국가 코드", example = "KR", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String countryCode;

    @Schema(description = "우편번호", example = "06236", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String postalCode;

    @Schema(description = "주·도·광역 지역", example = "서울특별시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String state;

    @Schema(description = "도시·시군구", example = "강남구", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String city;

    @Schema(description = "기본 주소", example = "테헤란로 123", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String addressLine1;

    @Schema(description = "상세 주소", example = "101동 1001호", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String addressLine2;

    @Schema(description = "기본 배송지 설정 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean isDefault;
}
