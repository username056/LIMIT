package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "ProductAuthenticityProofResponse", description = "정품 증빙 자료 항목")
public class ProductAuthenticityProofResponse {

    @Schema(description = "증빙 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long proofId;

    @Schema(description = "증빙 유형(영수증/보증서 등)", example = "RECEIPT", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String proofType;

    @Schema(description = "CDN 접근 URL, 비공개 증빙은 null", example = "https://cdn.example.com/...", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String cdnUrl;
}
