package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.product.dto.response.FavoriteProductResponse;
import com.c203.limit.domain.product.dto.response.FavoriteStatusResponse;
import com.c203.limit.domain.product.service.WishlistCreateResult;
import com.c203.limit.domain.product.service.WishlistService;
import com.c203.limit.domain.product.service.WishlistService.FavoritePage;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.response.PageMetaResponse;
import com.c203.limit.global.security.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WishlistController implements WishlistApi {
    private final WishlistService wishlistService;
    private final CurrentUser currentUser;

    public WishlistController(WishlistService wishlistService, CurrentUser currentUser) {
        this.wishlistService = wishlistService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<ApiResponse<FavoriteProductResponse>> addFavorite(Long productId) {
        WishlistCreateResult result = wishlistService.add(currentUser.memberId(), productId);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(result.response()));
    }

    @Override
    public ResponseEntity<ApiResponse<List<FavoriteProductResponse>>> getMyFavorites(int page, int size) {
        FavoritePage result = wishlistService.findAll(currentUser.memberId(), page, size);
        PageMetaResponse meta = new PageMetaResponse(
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasNext());
        return ResponseEntity.ok(new ApiResponse<>(result.content(), meta));
    }

    @Override
    public ResponseEntity<ApiResponse<FavoriteStatusResponse>> getFavoriteStatus(Long productId) {
        return ResponseEntity.ok(
                ApiResponse.ok(wishlistService.status(currentUser.memberId(), productId)));
    }

    @Override
    public ResponseEntity<Void> removeFavorite(Long productId) {
        wishlistService.remove(currentUser.memberId(), productId);
        return ResponseEntity.noContent().build();
    }
}
