package com.c203.limit.product.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "UpdateProductRequest", description = "상품 부분 수정 요청")
public class UpdateProductRequest {

    @Schema(description = "수정할 가격, null이면 미변경", example = "249000", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final BigDecimal price;

    @Schema(description = "수정할 설명, null이면 미변경", example = "설명 수정본입니다.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String description;

    @Schema(description = "수정할 재고 수량, null이면 미변경", example = "40", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Integer quantity;
}
