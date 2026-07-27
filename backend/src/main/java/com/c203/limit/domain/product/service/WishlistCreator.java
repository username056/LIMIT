package com.c203.limit.domain.product.service;

import com.c203.limit.domain.product.dto.response.FavoriteProductResponse;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.repository.WishlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WishlistCreator {
    private static final Logger log = LoggerFactory.getLogger(WishlistCreator.class);

    private final WishlistRepository wishlistRepository;

    public WishlistCreator(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WishlistCreateResult createOrGet(Long memberId, Listing listing) {
        boolean created = wishlistRepository.insertIgnore(memberId, listing.getId()) == 1;
        var wishlist = wishlistRepository
                .findByUserIdAndListingId(memberId, listing.getId())
                .orElseThrow(() -> new IllegalStateException("wishlist insert result was not readable"));
        if (created) {
            log.info("wishlist added: productId={}", listing.getId());
            return WishlistCreateResult.created(
                    FavoriteProductResponse.from(wishlist));
        }
        return WishlistCreateResult.existing(FavoriteProductResponse.from(wishlist));
    }
}
