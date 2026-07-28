package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.Wishlist;
import com.c203.limit.domain.product.repository.WishlistRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WishlistCreatorTests {
    private static final Long MEMBER_ID = 20L;
    private static final Long PRODUCT_ID = 100L;

    @Mock WishlistRepository wishlistRepository;
    WishlistCreator creator;
    Listing listing;
    Wishlist wishlist;

    @BeforeEach
    void setUp() {
        creator = new WishlistCreator(wishlistRepository);
        Category category = Category.createTopLevel("Galaxy S24", DeviceType.SMARTPHONE, 1);
        listing = Listing.createDraft(30L, category, "Galaxy S24", null, 650000, 5L);
        ReflectionTestUtils.setField(listing, "id", PRODUCT_ID);
        wishlist = Wishlist.create(MEMBER_ID, listing);
        ReflectionTestUtils.setField(wishlist, "id", 501L);
    }

    @Test
    void returnsCreatedResultWhenInsertWins() {
        when(wishlistRepository.insertIgnore(MEMBER_ID, PRODUCT_ID)).thenReturn(1);
        when(wishlistRepository.findByUserIdAndListingId(MEMBER_ID, PRODUCT_ID))
                .thenReturn(Optional.of(wishlist));

        WishlistCreateResult result = creator.createOrGet(MEMBER_ID, listing);

        assertThat(result.created()).isTrue();
        assertThat(result.response().favoriteId()).isEqualTo(501L);
    }

    @Test
    void returnsExistingResultWhenConcurrentInsertWins() {
        when(wishlistRepository.insertIgnore(MEMBER_ID, PRODUCT_ID)).thenReturn(0);
        when(wishlistRepository.findByUserIdAndListingId(MEMBER_ID, PRODUCT_ID))
                .thenReturn(Optional.of(wishlist));

        WishlistCreateResult result = creator.createOrGet(MEMBER_ID, listing);

        assertThat(result.created()).isFalse();
        assertThat(result.response().favoriteId()).isEqualTo(501L);
    }
}
