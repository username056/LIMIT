package com.c203.limit.domain.product.dto.response;

import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.Wishlist;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(name = "FavoriteProductResponse", description = "회원이 저장한 관심 상품")
public record FavoriteProductResponse(
        @Schema(example = "501") Long favoriteId,
        @Schema(example = "1001") Long productId,
        @Schema(example = "Galaxy S24 256GB") String name,
        @Schema(example = "Samsung") String manufacturerName,
        @Schema(example = "Galaxy S24") String modelName,
        @Schema(example = "650000") BigDecimal price,
        @Schema(example = "ON_SALE") String status,
        @Schema(description = "대표 이미지 URL. 등록된 이미지가 없으면 null")
                String thumbnailUrl,
        @Schema(example = "2026-07-27T10:00:00") LocalDateTime favoritedAt) {

    public static FavoriteProductResponse from(Wishlist wishlist) {
        return from(wishlist, null);
    }

    /**
     * @param thumbnailUrl 대표 이미지 URL. 이미지는 별도 테이블이라 목록을 한 번에 조회한 뒤
     *     넣어 준다(항목마다 조회하면 N+1이 된다).
     */
    public static FavoriteProductResponse from(Wishlist wishlist, String thumbnailUrl) {
        Listing listing = wishlist.getListing();
        Category category = listing.getCategory();
        return new FavoriteProductResponse(
                wishlist.getId(),
                listing.getId(),
                listing.getTitle(),
                // 카탈로그에 없어 직접 입력한 기기는 그 값을 보여 준다. 상품 목록·상세와 같은 규칙이다.
                listing.getCustomManufacturer() != null
                        ? listing.getCustomManufacturer()
                        : category.getManufacturer(),
                listing.getCustomModelName() != null
                        ? listing.getCustomModelName()
                        : category.getName(),
                BigDecimal.valueOf(listing.getPrice()),
                listing.getStatus().name(),
                thumbnailUrl,
                wishlist.getCreatedAt());
    }
}
