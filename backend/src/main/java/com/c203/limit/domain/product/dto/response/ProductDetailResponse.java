package com.c203.limit.domain.product.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ProductDetailResponse", description = "중고 전자기기 상품 상세")
public class ProductDetailResponse {

    @Schema(example = "1001")
    private final Long productId;

    @Schema(example = "55")
    private final Long sellerId;

    private final DeviceCategoryResponse category;

    private final DeviceInfoResponse device;

    @Schema(example = "Galaxy S24 256GB")
    private final String name;

    @Schema(example = "생활 흠집이 있습니다.")
    private final String description;

    @Schema(example = "650000")
    private final BigDecimal price;

    @Schema(example = "ON_SALE")
    private final String status;

    @Schema(example = "서울 강남구")
    private final String tradeRegion;

    private final ChecklistSummaryResponse checklistSummary;

    @Schema(example = "https://cdn.example.com/products/1001/thumbnail.jpg")
    private final String thumbnailUrl;

    @Schema(example = "2026-07-22T10:00:00+09:00")
    private final OffsetDateTime createdAt;

    @Schema(example = "2026-07-22T11:00:00+09:00")
    private final OffsetDateTime updatedAt;

    // 아래 셋은 구매자가 이 매물에 얼마나 관심이 몰렸는지 가늠하는 값이다.
    // 조회수는 listing.view_count, 나머지 둘은 wishlist·chat_room의 행 수다.
    @Schema(description = "상세 조회 수", example = "128")
    private final Long viewCount;

    @Schema(description = "좋아요한 회원 수", example = "12")
    private final Long favoriteCount;

    @Schema(description = "이 매물로 열린 문의 채팅방 수", example = "3")
    private final Long chatRoomCount;

    @Schema(description = "카탈로그에 없는 기기를 직접 입력해 등록했는지 여부. 커스텀 모델은 수정 시 기능 체크리스트를 바꿀 수 없다.")
    private final boolean customModel;

    @Schema(description = "판매자가 확정한 선택 기능 코드 목록. 수정 화면 체크박스 복원에 그대로 쓸 수 있다.")
    private final List<String> confirmedFeatures;
}
