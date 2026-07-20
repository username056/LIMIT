package com.c203.limit.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "AdminSellerSearchRequest", description = "관리자 판매자 검색 조건")
public class AdminSellerSearchRequest {

    @Schema(description = "페이지 번호", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int page;

    @Schema(description = "페이지 크기", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
    private final int size;

    @Schema(description = "판매자 상태", example = "ACTIVE", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String status;

    @Schema(description = "판매자 유형", example = "INDIVIDUAL", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String sellerType;

    @Schema(description = "국가 코드", example = "US", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String countryCode;

    @Schema(description = "이메일·상호명 검색어", example = "open", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String keyword;
}
