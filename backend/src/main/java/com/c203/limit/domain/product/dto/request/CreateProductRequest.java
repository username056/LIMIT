package com.c203.limit.domain.product.dto.request;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "CreateProductRequest", description = "중고 전자기기 상품 초안 등록 요청")
public class CreateProductRequest {

    @Schema(description = "기기 카테고리 ID", example = "1")
    @NotNull
    private final Long categoryId;

    @Schema(description = "기기 모델 ID", example = "101")
    @NotNull
    private final Long deviceModelId;

    @Schema(description = "상품명", example = "Galaxy S24 256GB")
    @NotBlank
    @Size(max = 100)
    private final String name;

    @Schema(description = "상품 설명", example = "생활 흠집이 있습니다.")
    @Size(max = 2000)
    private final String description;

    @Schema(description = "판매 가격", example = "650000")
    @NotNull
    @Positive
    private final BigDecimal price;

    @Schema(description = "색상", example = "Onyx Black")
    @Size(max = 50)
    private final String color;

    @Schema(description = "저장 용량(GB)", example = "256")
    @Min(1)
    private final Integer storageGb;

    @Schema(description = "거래 지역", example = "서울 강남구")
    @NotBlank
    @Size(max = 100)
    private final String tradeRegion;
}
