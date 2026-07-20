package com.c203.limit.domain.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "ProductImageResponse", description = "상품 이미지 항목")
public class ProductImageResponse {

    @Schema(description = "이미지 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long imageId;

    @Schema(description = "THUMBNAIL | DETAIL", example = "THUMBNAIL", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String imageType;

    @Schema(description = "CloudFront 접근 URL", example = "https://cdn.example.com/...", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String cdnUrl;
}
