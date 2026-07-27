package com.c203.limit.domain.product.service;

import com.c203.limit.domain.product.dto.response.FavoriteProductResponse;
import com.c203.limit.domain.product.dto.response.FavoriteStatusResponse;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.entity.Wishlist;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.WishlistRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistService {
    private static final Logger log = LoggerFactory.getLogger(WishlistService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final WishlistRepository wishlistRepository;
    private final ListingRepository listingRepository;
    private final WishlistCreator creator;

    public WishlistService(
            WishlistRepository wishlistRepository,
            ListingRepository listingRepository,
            WishlistCreator creator) {
        this.wishlistRepository = wishlistRepository;
        this.listingRepository = listingRepository;
        this.creator = creator;
    }

    @Transactional(readOnly = true)
    public WishlistCreateResult add(Long memberId, Long productId) {
        Listing listing = listingRepository
                .findByIdAndStatusAndDeletedAtIsNull(productId, ListingStatus.ON_SALE)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        return creator.createOrGet(memberId, listing);
    }

    @Transactional(readOnly = true)
    public FavoriteStatusResponse status(Long memberId, Long productId) {
        if (listingRepository
                .findByIdAndStatusAndDeletedAtIsNull(productId, ListingStatus.ON_SALE)
                .isEmpty()) {
            throw new BusinessException(ErrorCode.LISTING_NOT_FOUND);
        }
        return new FavoriteStatusResponse(
                wishlistRepository.existsByUserIdAndListingId(memberId, productId));
    }

    @Transactional(readOnly = true)
    public FavoritePage findAll(Long memberId, int page, int size) {
        validatePage(page, size);
        Page<Wishlist> result = wishlistRepository.findActiveByUserId(
                memberId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<FavoriteProductResponse> content =
                result.getContent().stream().map(FavoriteProductResponse::from).toList();
        return new FavoritePage(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    @Transactional
    public void remove(Long memberId, Long productId) {
        long deleted = wishlistRepository.deleteByUserIdAndListingId(memberId, productId);
        if (deleted > 0) {
            log.info("wishlist removed: productId={}", productId);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    public record FavoritePage(
            List<FavoriteProductResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}
}
