package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.product.dto.response.FavoriteProductResponse;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.entity.Wishlist;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.WishlistRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTests {
    private static final Long MEMBER_ID = 20L;
    private static final Long PRODUCT_ID = 100L;

    @Mock WishlistRepository wishlistRepository;
    @Mock ListingRepository listingRepository;
    @Mock WishlistCreator creator;
    WishlistService service;

    @BeforeEach
    void setUp() {
        service = new WishlistService(wishlistRepository, listingRepository, creator);
    }

    @Test
    void addsProductToWishlist() {
        Listing listing = listing();
        when(listingRepository.findByIdAndStatusAndDeletedAtIsNull(
                        PRODUCT_ID, ListingStatus.ON_SALE))
                .thenReturn(Optional.of(listing));
        Wishlist created = wishlist(listing);
        when(creator.createOrGet(MEMBER_ID, listing))
                .thenReturn(WishlistCreateResult.created(FavoriteProductResponse.from(created)));

        WishlistCreateResult result = service.add(MEMBER_ID, PRODUCT_ID);

        assertThat(result.created()).isTrue();
        assertThat(result.response().favoriteId()).isEqualTo(501L);
        assertThat(result.response().productId()).isEqualTo(PRODUCT_ID);
    }

    @Test
    void returnsExistingFavoriteForDuplicateRequest() {
        Listing listing = listing();
        Wishlist wishlist = wishlist(listing);
        when(listingRepository.findByIdAndStatusAndDeletedAtIsNull(
                        PRODUCT_ID, ListingStatus.ON_SALE))
                .thenReturn(Optional.of(listing));
        when(creator.createOrGet(MEMBER_ID, listing))
                .thenReturn(WishlistCreateResult.existing(FavoriteProductResponse.from(wishlist)));

        WishlistCreateResult result = service.add(MEMBER_ID, PRODUCT_ID);

        assertThat(result.created()).isFalse();
        verify(creator).createOrGet(MEMBER_ID, listing);
    }

    @Test
    void returnsExistingFavoriteCreatedByConcurrentRequest() {
        Listing listing = listing();
        Wishlist existing = wishlist(listing);
        when(listingRepository.findByIdAndStatusAndDeletedAtIsNull(
                        PRODUCT_ID, ListingStatus.ON_SALE))
                .thenReturn(Optional.of(listing));
        when(creator.createOrGet(MEMBER_ID, listing))
                .thenReturn(WishlistCreateResult.existing(FavoriteProductResponse.from(existing)));

        WishlistCreateResult result = service.add(MEMBER_ID, PRODUCT_ID);

        assertThat(result.created()).isFalse();
        assertThat(result.response().favoriteId()).isEqualTo(501L);
    }

    @Test
    void rejectsMissingOrDeletedListing() {
        when(listingRepository.findByIdAndStatusAndDeletedAtIsNull(
                        PRODUCT_ID, ListingStatus.ON_SALE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(MEMBER_ID, PRODUCT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_FOUND));
        verifyNoInteractions(wishlistRepository);
    }

    @Test
    void returnsFavoritesNewestFirstWithPageMetadata() {
        Wishlist wishlist = wishlist(listing());
        when(wishlistRepository.findActiveByUserId(eq(MEMBER_ID), any(Pageable.class)))
                .thenAnswer(
                        invocation ->
                                new PageImpl<>(
                                        List.of(wishlist), invocation.getArgument(1), 1));

        WishlistService.FavoritePage result = service.findAll(MEMBER_ID, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).name()).isEqualTo("Galaxy S24");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void returnsCurrentMembersFavoriteStatus() {
        when(listingRepository.findByIdAndStatusAndDeletedAtIsNull(
                        PRODUCT_ID, ListingStatus.ON_SALE))
                .thenReturn(Optional.of(listing()));
        when(wishlistRepository.existsByUserIdAndListingId(MEMBER_ID, PRODUCT_ID))
                .thenReturn(true);

        assertThat(service.status(MEMBER_ID, PRODUCT_ID).favorite()).isTrue();
    }

    @Test
    void rejectsInvalidPageSize() {
        assertThatThrownBy(() -> service.findAll(MEMBER_ID, 0, 101))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(wishlistRepository);
    }

    @Test
    void removesOnlyCurrentMembersFavorite() {
        when(wishlistRepository.deleteByUserIdAndListingId(MEMBER_ID, PRODUCT_ID)).thenReturn(1L);

        service.remove(MEMBER_ID, PRODUCT_ID);

        verify(wishlistRepository).deleteByUserIdAndListingId(MEMBER_ID, PRODUCT_ID);
    }

    private Listing listing() {
        Category category = Category.createTopLevel("Galaxy S24", DeviceType.SMARTPHONE, 1);
        ReflectionTestUtils.setField(category, "id", 10L);
        ReflectionTestUtils.setField(category, "manufacturer", "Samsung");
        Listing listing = Listing.createDraft(MEMBER_ID + 1, category, "Galaxy S24", "상태 양호", 650000, 5L);
        ReflectionTestUtils.setField(listing, "id", PRODUCT_ID);
        return listing;
    }

    private Wishlist wishlist(Listing listing) {
        Wishlist wishlist = Wishlist.create(MEMBER_ID, listing);
        ReflectionTestUtils.setField(wishlist, "id", 501L);
        return wishlist;
    }
}
