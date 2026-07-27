package com.c203.limit.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ListingTests {

    private Listing onSaleListing() {
        Category category = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 0);
        Listing listing = Listing.createDraft(1L, category, "갤럭시 S24", "설명", 650_000, 10L);
        ReflectionTestUtils.setField(listing, "status", ListingStatus.ON_SALE);
        return listing;
    }

    @Test
    void reserveMovesOnSaleListingToReserved() {
        Listing listing = onSaleListing();

        listing.reserve(2L);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);
        assertThat(listing.getBuyerId()).isEqualTo(2L);
        assertThat(listing.getReservedAt()).isNotNull();
    }

    @Test
    void reserveRejectsListingThatIsNotOnSale() {
        Category category = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 0);
        Listing draft = Listing.createDraft(1L, category, "갤럭시 S24", "설명", 650_000, 10L);

        assertThatThrownBy(() -> draft.reserve(2L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_ON_SALE));
    }

    @Test
    void reserveRejectsDuplicateCallOnAlreadyReservedListing() {
        Listing listing = onSaleListing();
        listing.reserve(2L);

        assertThatThrownBy(() -> listing.reserve(3L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_ON_SALE));
    }

    @Test
    void cancelReservationReturnsListingToOnSaleAndClearsBuyer() {
        Listing listing = onSaleListing();
        listing.reserve(2L);

        listing.cancelReservation();

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ON_SALE);
        assertThat(listing.getBuyerId()).isNull();
        assertThat(listing.getReservedAt()).isNull();
    }

    @Test
    void cancelReservationRejectsListingThatIsNotReserved() {
        Listing listing = onSaleListing();

        assertThatThrownBy(listing::cancelReservation)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_RESERVED));
    }

    @Test
    void expireReservationReturnsListingToOnSaleAndClearsBuyer() {
        Listing listing = onSaleListing();
        listing.reserve(2L);

        listing.expireReservation();

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ON_SALE);
        assertThat(listing.getBuyerId()).isNull();
    }

    @Test
    void expireReservationRejectsListingThatIsNotReserved() {
        Listing listing = onSaleListing();

        assertThatThrownBy(listing::expireReservation)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_RESERVED));
    }

    @Test
    void markPaidRequiresReservedListing() {
        Listing listing = onSaleListing();

        assertThatThrownBy(listing::markPaid)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_RESERVED));

        listing.reserve(2L);
        listing.markPaid();

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.PAID);
        assertThat(listing.getPaidAt()).isNotNull();
    }

    @Test
    void markInspectingRequiresPaidListing() {
        Listing listing = onSaleListing();

        assertThatThrownBy(listing::markInspecting)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_PAID));

        listing.reserve(2L);
        listing.markPaid();
        listing.markInspecting();

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.INSPECTING);
    }

    @Test
    void confirmRequiresInspectingListing() {
        Listing listing = onSaleListing();
        listing.reserve(2L);
        listing.markPaid();

        assertThatThrownBy(listing::confirm)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_INSPECTING));

        listing.markInspecting();
        listing.confirm();

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.CONFIRMED);
        assertThat(listing.getConfirmedAt()).isNotNull();
    }

    @Test
    void settleRequiresConfirmedListing() {
        Listing listing = onSaleListing();
        listing.reserve(2L);
        listing.markPaid();
        listing.markInspecting();

        assertThatThrownBy(listing::settle)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_CONFIRMED));

        listing.confirm();
        listing.settle();

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SETTLED);
        assertThat(listing.getSettledAt()).isNotNull();
    }
}
