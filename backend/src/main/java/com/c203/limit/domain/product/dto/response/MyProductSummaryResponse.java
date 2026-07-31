package com.c203.limit.domain.product.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "MyProductSummaryResponse", description = "내 상품 목록 항목")
public class MyProductSummaryResponse {

    @Schema(example = "1001")
    private final Long productId;

    @Schema(example = "Galaxy S24 256GB")
    private final String name;

    @Schema(example = "VERIFYING")
    private final String status;

    // 아래 네 필드는 판매자가 목록에서 상품을 알아보기 위한 값이다. 공개 목록
    // (ProductSummaryResponse)에는 있었지만 내 상품 목록에는 빠져 있어, 상품 관리 화면이
    // 대표 이미지와 기기 정보를 비워 둔 채로 보여 주고 있었다.
    @Schema(description = "제조사. 카탈로그에 없으면 직접 입력값", example = "Samsung")
    private final String manufacturerName;

    @Schema(description = "모델명. 카탈로그에 없으면 직접 입력값", example = "Galaxy S24")
    private final String modelName;

    @Schema(example = "650000")
    private final BigDecimal price;

    @Schema(description = "대표 이미지 URL. 등록 전이면 null", example = "https://cdn.example.com/1001.jpg")
    private final String thumbnailUrl;

    @Schema(example = "12")
    private final Integer completedItemCount;

    @Schema(example = "18")
    private final Integer requiredItemCount;

    @Schema(example = "2026-07-22T11:00:00+09:00")
    private final OffsetDateTime updatedAt;
}
