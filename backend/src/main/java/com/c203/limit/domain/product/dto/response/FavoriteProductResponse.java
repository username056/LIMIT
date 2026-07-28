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
        @Schema(example = "2026-07-27T10:00:00") LocalDateTime favoritedAt) {

    public static FavoriteProductResponse from(Wishlist wishlist) {
        Listing listing = wishlist.getListing();
        Category category = listing.getCategory();
        return new FavoriteProductResponse(
                wishlist.getId(),
                listing.getId(),
                listing.getTitle(),
                category.getManufacturer(),
                category.getName(),
                BigDecimal.valueOf(listing.getPrice()),
                listing.getStatus().name(),
                wishlist.getCreatedAt());
    }
}
