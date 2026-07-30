package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.product.entity.Category;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.entity.ListingStatusHistory;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ListingServiceTests {

    private static final Long LISTING_ID = 100L;
    private static final Long BUYER_ID = 2L;
    private static final long RESERVATION_TTL_MINUTES = 30;
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneId.systemDefault());

    @Mock ListingRepository listingRepository;
    @Mock ListingStatusHistoryRepository listingStatusHistoryRepository;

    ListingService service;

    @BeforeEach
    void setUp() {
        service = new ListingService(
                listingRepository,
                listingStatusHistoryRepository,
                FIXED_CLOCK,
                RESERVATION_TTL_MINUTES);
    }

    private Listing listingWithStatus(ListingStatus status) {
        Category category = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 0);
        Listing listing = Listing.createDraft(1L, category, "갤럭시 S24", "설명", 650_000, 10L);
        ReflectionTestUtils.setField(listing, "id", LISTING_ID);
        ReflectionTestUtils.setField(listing, "status", status);
        return listing;
    }

    @Test
    void reserveTransitionsListingAndRecordsHistory() {
        Listing listing = listingWithStatus(ListingStatus.ON_SALE);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        ListingReservationView result = service.reserve(LISTING_ID, BUYER_ID);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);
        assertThat(listing.getBuyerId()).isEqualTo(BUYER_ID);
        assertThat(listing.getReservedUntil())
                .isEqualTo(LocalDateTime.now(FIXED_CLOCK).plusMinutes(RESERVATION_TTL_MINUTES));
        assertThat(result.sellerId()).isEqualTo(listing.getSellerId());
        assertThat(result.price()).isEqualTo(listing.getPrice());
        verify(listingStatusHistoryRepository).save(any(ListingStatusHistory.class));
    }

    @Test
    void isReservationActiveReturnsTrueWhileReservedBeforeDeadline() {
        Listing listing = listingWithStatus(ListingStatus.RESERVED);
        ReflectionTestUtils.setField(listing, "buyerId", BUYER_ID);
        ReflectionTestUtils.setField(
                listing, "reservedUntil", LocalDateTime.now(FIXED_CLOCK).plusMinutes(1));
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        assertThat(service.isReservationActive(LISTING_ID, BUYER_ID)).isTrue();
    }

    @Test
    void isReservationActiveReturnsFalseAfterDeadlinePassed() {
        Listing listing = listingWithStatus(ListingStatus.RESERVED);
        ReflectionTestUtils.setField(listing, "buyerId", BUYER_ID);
        ReflectionTestUtils.setField(
                listing, "reservedUntil", LocalDateTime.now(FIXED_CLOCK).minusMinutes(1));
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        assertThat(service.isReservationActive(LISTING_ID, BUYER_ID)).isFalse();
    }

    @Test
    void isReservationActiveReturnsFalseForDifferentBuyer() {
        Listing listing = listingWithStatus(ListingStatus.RESERVED);
        ReflectionTestUtils.setField(listing, "buyerId", BUYER_ID);
        ReflectionTestUtils.setField(
                listing, "reservedUntil", LocalDateTime.now(FIXED_CLOCK).plusMinutes(1));
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        assertThat(service.isReservationActive(LISTING_ID, BUYER_ID + 1)).isFalse();
    }

    @Test
    void isReservationActiveReturnsFalseWhenNotReserved() {
        Listing listing = listingWithStatus(ListingStatus.ON_SALE);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        assertThat(service.isReservationActive(LISTING_ID, BUYER_ID)).isFalse();
    }

    @Test
    void getReturnsReservationViewWithoutMutatingListing() {
        Listing listing = listingWithStatus(ListingStatus.ON_SALE);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        ListingReservationView result = service.get(LISTING_ID);

        assertThat(result.sellerId()).isEqualTo(listing.getSellerId());
        assertThat(result.price()).isEqualTo(listing.getPrice());
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ON_SALE);
        verifyNoInteractions(listingStatusHistoryRepository);
    }

    @Test
    void getThrowsWhenListingNotFound() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(LISTING_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_FOUND));
    }

    @Test
    void reserveThrowsWhenListingNotFound() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserve(LISTING_ID, BUYER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_FOUND));

        verifyNoInteractions(listingStatusHistoryRepository);
    }

    @Test
    void reserveRejectsListingThatIsNotOnSaleAndSkipsHistory() {
        Listing listing = listingWithStatus(ListingStatus.DRAFT);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.reserve(LISTING_ID, BUYER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_ON_SALE));

        verifyNoInteractions(listingStatusHistoryRepository);
    }

    @Test
    void markPaidTransitionsListingAndRecordsHistory() {
        Listing listing = listingWithStatus(ListingStatus.RESERVED);
        ReflectionTestUtils.setField(listing, "buyerId", BUYER_ID);
        ReflectionTestUtils.setField(
                listing, "reservedUntil", LocalDateTime.now(FIXED_CLOCK).plusMinutes(1));
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        Listing result = service.markPaid(LISTING_ID, BUYER_ID);

        assertThat(result.getStatus()).isEqualTo(ListingStatus.PAID);
        verify(listingStatusHistoryRepository).save(any(ListingStatusHistory.class));
    }

    @Test
    void markPaidRejectsWhenReservationBelongsToAnotherBuyer() {
        Listing listing = listingWithStatus(ListingStatus.RESERVED);
        ReflectionTestUtils.setField(listing, "buyerId", BUYER_ID);
        ReflectionTestUtils.setField(
                listing, "reservedUntil", LocalDateTime.now(FIXED_CLOCK).plusMinutes(1));
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.markPaid(LISTING_ID, BUYER_ID + 1))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_RESERVATION_MISMATCH));
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);
        verifyNoInteractions(listingStatusHistoryRepository);
    }

    @Test
    void cancelReservationReturnsListingToOnSaleAndRecordsHistory() {
        Listing listing = listingWithStatus(ListingStatus.RESERVED);
        ReflectionTestUtils.setField(listing, "buyerId", BUYER_ID);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        Listing result = service.cancelReservation(LISTING_ID, BUYER_ID, "구매자 변심");

        assertThat(result.getStatus()).isEqualTo(ListingStatus.ON_SALE);
        assertThat(result.getBuyerId()).isNull();
        verify(listingStatusHistoryRepository).save(any(ListingStatusHistory.class));
    }

    @Test
    void markInspectingRequiresPaidListing() {
        Listing listing = listingWithStatus(ListingStatus.RESERVED);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.markInspecting(LISTING_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_PAID));

        verifyNoInteractions(listingStatusHistoryRepository);
    }
}
