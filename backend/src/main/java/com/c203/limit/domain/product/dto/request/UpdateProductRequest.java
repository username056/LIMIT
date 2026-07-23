package com.c203.limit.domain.product.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "UpdateProductRequest", description = "중고 전자기기 상품 부분 수정 요청")
public class UpdateProductRequest {

    @Schema(description = "상품명", example = "Galaxy S24 256GB 자급제")
    @Size(max = 100)
    private final String name;

    @Schema(description = "상품 설명", example = "생활 흠집이 있습니다. 구성품은 기기 단품입니다.")
    @Size(max = 2000)
    private final String description;

    @Schema(description = "판매 가격", example = "630000")
    @Positive
    private final BigDecimal price;

    @Schema(description = "색상", example = "Onyx Black")
    @Size(max = 50)
    private final String color;

    @Schema(description = "저장 용량(GB)", example = "256")
    @Min(1)
    private final Integer storageGb;

    @Schema(description = "거래 지역", example = "서울 송파구")
    @Size(max = 100)
    private final String tradeRegion;
}
