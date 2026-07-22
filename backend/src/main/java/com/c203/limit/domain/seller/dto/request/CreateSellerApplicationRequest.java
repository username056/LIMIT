package com.c203.limit.domain.seller.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "CreateSellerApplicationRequest", description = "판매자 신청서 초안 생성")
public class CreateSellerApplicationRequest {

    @Schema(description = "개인·기업 판매자 구분", example = "INDIVIDUAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String sellerType;
}
