package com.c203.limit.domain.product.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ProductSummaryResponse", description = "공개 상품 목록 항목")
public class ProductSummaryResponse {

    @Schema(example = "1001")
    private final Long productId;

    @Schema(example = "Galaxy S24 256GB")
    private final String name;

    @Schema(example = "Samsung")
    private final String manufacturerName;

    @Schema(example = "Galaxy S24")
    private final String modelName;

    @Schema(example = "650000")
    private final BigDecimal price;

    @Schema(example = "ON_SALE")
    private final String status;

    @Schema(example = "COMPLETED")
    private final String verificationStatus;

    // 목록 화면이 "검증 n개"를 보여 주고 개수로 거르기 때문에 상태값만으로는 부족하다.
    // 예전에는 개수가 없어서 프런트가 구간 선택을 COMPLETED/IN_PROGRESS 둘로 뭉개고 있었다.
    @Schema(description = "완료한 필수 검증 항목 수", example = "8")
    private final Integer completedItemCount;

    @Schema(description = "필수 검증 항목 수", example = "10")
    private final Integer requiredItemCount;

    @Schema(example = "https://cdn.example.com/products/1001/thumbnail.jpg")
    private final String thumbnailUrl;

    @Schema(example = "서울 강남구")
    private final String tradeRegion;

    @Schema(description = "공개 상세 조회 수", example = "128")
    private final Long viewCount;
}
