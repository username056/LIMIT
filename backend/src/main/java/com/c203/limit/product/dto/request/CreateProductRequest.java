package com.c203.limit.product.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "CreateProductRequest", description = "상품 등록 요청(멀티파트 request part)")
public class CreateProductRequest {

    @Schema(description = "브랜드 ID", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long brandId;

    @Schema(description = "카테고리 ID", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long categoryId;

    @Schema(description = "상품명", example = "Air Jordan 1 Retro High OG", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String name;

    @Schema(description = "상품 설명", example = "정품 미개봉 제품입니다.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String description;

    @Schema(description = "판매 가격", example = "259000", requiredMode = Schema.RequiredMode.REQUIRED)
    private final BigDecimal price;

    @Schema(description = "초기 재고 수량(inventory 생성에 사용)", example = "50", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Integer quantity;
}
