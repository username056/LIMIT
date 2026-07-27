package com.c203.limit.domain.product.service;

import com.c203.limit.domain.product.dto.response.FavoriteProductResponse;

public record WishlistCreateResult(FavoriteProductResponse response, boolean created) {
    public static WishlistCreateResult created(FavoriteProductResponse response) {
        return new WishlistCreateResult(response, true);
    }

    public static WishlistCreateResult existing(FavoriteProductResponse response) {
        return new WishlistCreateResult(response, false);
    }
}
