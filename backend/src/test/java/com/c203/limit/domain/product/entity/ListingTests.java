package com.c203.limit.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.domain.inspection.enums.DeviceCheckResult;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.enums.TestType;
import com.c203.limit.domain.product.moderation.entity.ListingModerationStatus;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ListingTests {

    private static final LocalDateTime RESERVED_UNTIL = LocalDateTime.of(2026, 7, 28, 0, 30);

    private Listing draftListing() {
        Category category = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 0);
        return Listing.createDraft(1L, category, "갤럭시 S24", "설명", 650_000, 10L);
    }

    private Listing listingWithStatus(ListingStatus status) {
        Listing listing = draftListing();
        ReflectionTestUtils.setField(listing, "status", status);
        return listing;
    }

    private Listing onSaleListing() {
        return listingWithStatus(ListingStatus.ON_SALE);
    }

    @Test
    void reserveMovesOnSaleListingToReserved() {
        Listing listing = onSaleListing();

        listing.reserve(2L, RESERVED_UNTIL);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);
        assertThat(listing.getBuyerId()).isEqualTo(2L);
        assertThat(listing.getReservedAt()).isNotNull();
        assertThat(listing.getReservedUntil()).isEqualTo(RESERVED_UNTIL);
    }

    @Test
    void reserveRejectsListingThatIsNotOnSale() {
        Category category = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 0);
        Listing draft = Listing.createDraft(1L, category, "갤럭시 S24", "설명", 650_000, 10L);

        assertThatThrownBy(() -> draft.reserve(2L, RESERVED_UNTIL))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_ON_SALE));
    }

    @Test
    void reserveRejectsDuplicateCallOnAlreadyReservedListing() {
        Listing listing = onSaleListing();
        listing.reserve(2L, RESERVED_UNTIL);

        assertThatThrownBy(() -> listing.reserve(3L, RESERVED_UNTIL))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_ON_SALE));
    }

    @Test
    void cancelReservationReturnsListingToOnSaleAndClearsBuyer() {
        Listing listing = onSaleListing();
        listing.reserve(2L, RESERVED_UNTIL);

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
        listing.reserve(2L, RESERVED_UNTIL);

        listing.expireReservation();

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ON_SALE);
        assertThat(listing.getBuyerId()).isNull();
        assertThat(listing.getReservedAt()).isNull();
        assertThat(listing.getReservedUntil()).isNull();
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
        LocalDateTime now = RESERVED_UNTIL.minusMinutes(1);

        assertThatThrownBy(() -> listing.markPaid(2L, now))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_RESERVED));

        listing.reserve(2L, RESERVED_UNTIL);
        listing.markPaid(2L, now);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.PAID);
        assertThat(listing.getPaidAt()).isNotNull();
    }

    @Test
    void markPaidRejectsMismatchedBuyer() {
        Listing listing = onSaleListing();
        listing.reserve(2L, RESERVED_UNTIL);

        assertThatThrownBy(() -> listing.markPaid(3L, RESERVED_UNTIL.minusMinutes(1)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_RESERVATION_MISMATCH));
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);
    }

    @Test
    void markPaidRejectsExpiredReservation() {
        Listing listing = onSaleListing();
        listing.reserve(2L, RESERVED_UNTIL);

        assertThatThrownBy(() -> listing.markPaid(2L, RESERVED_UNTIL.plusSeconds(1)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_RESERVATION_MISMATCH));
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);
    }

    @Test
    void markPaidRecoveredFromPgAllowsExpiredReservationForSameBuyer() {
        Listing listing = onSaleListing();
        listing.reserve(2L, RESERVED_UNTIL);

        listing.markPaidRecoveredFromPg(2L, RESERVED_UNTIL.plusMinutes(30));

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.PAID);
        assertThat(listing.getPaidAt()).isNotNull();
    }

    @Test
    void markPaidRecoveredFromPgRequiresReservedListing() {
        Listing listing = onSaleListing();

        assertThatThrownBy(() -> listing.markPaidRecoveredFromPg(2L, RESERVED_UNTIL))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_RESERVED));
    }

    @Test
    void markPaidRecoveredFromPgRejectsMismatchedBuyer() {
        Listing listing = onSaleListing();
        listing.reserve(2L, RESERVED_UNTIL);

        assertThatThrownBy(() -> listing.markPaidRecoveredFromPg(3L, RESERVED_UNTIL.plusMinutes(30)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_RESERVATION_MISMATCH));
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);
    }

    @Test
    void renewReservationForBuyerExtendsReservedUntilForSameBuyer() {
        Listing listing = onSaleListing();
        listing.reserve(2L, RESERVED_UNTIL);
        LocalDateTime renewedUntil = RESERVED_UNTIL.plusMinutes(10);

        listing.renewReservationForBuyer(2L, renewedUntil);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);
        assertThat(listing.getReservedUntil()).isEqualTo(renewedUntil);
    }

    @Test
    void renewReservationForBuyerRequiresReservedListing() {
        Listing listing = onSaleListing();

        assertThatThrownBy(() -> listing.renewReservationForBuyer(2L, RESERVED_UNTIL))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_RESERVED));
    }

    @Test
    void renewReservationForBuyerRejectsMismatchedBuyer() {
        Listing listing = onSaleListing();
        listing.reserve(2L, RESERVED_UNTIL);

        assertThatThrownBy(() -> listing.renewReservationForBuyer(3L, RESERVED_UNTIL.plusMinutes(10)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_RESERVATION_MISMATCH));
        assertThat(listing.getReservedUntil()).isEqualTo(RESERVED_UNTIL);
    }

    @Test
    void enterInspectionRequiresPaidListingAndRecordsHandoverAndAutoConfirmDeadline() {
        Listing listing = onSaleListing();
        LocalDateTime handedOverAt = RESERVED_UNTIL;
        LocalDateTime autoConfirmAt = handedOverAt.plusDays(7);

        assertThatThrownBy(() -> listing.enterInspection(handedOverAt, autoConfirmAt))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_PAID));

        listing.reserve(2L, RESERVED_UNTIL);
        listing.markPaid(2L, RESERVED_UNTIL.minusMinutes(1));
        listing.enterInspection(handedOverAt, autoConfirmAt);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.INSPECTING);
        assertThat(listing.getHandedOverAt()).isEqualTo(handedOverAt);
        assertThat(listing.getAutoConfirmAt()).isEqualTo(autoConfirmAt);
    }

    @Test
    void confirmRequiresInspectingListing() {
        Listing listing = onSaleListing();
        listing.reserve(2L, RESERVED_UNTIL);
        listing.markPaid(2L, RESERVED_UNTIL.minusMinutes(1));

        LocalDateTime confirmedAt = RESERVED_UNTIL.plusDays(1);
        assertThatThrownBy(() -> listing.confirm(2L, confirmedAt))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_INSPECTING));

        listing.enterInspection(RESERVED_UNTIL, RESERVED_UNTIL.plusDays(7));
        listing.confirm(2L, confirmedAt);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.CONFIRMED);
        assertThat(listing.getConfirmedAt()).isEqualTo(confirmedAt);
    }

    @Test
    void confirmRejectsMismatchedBuyer() {
        Listing listing = onSaleListing();
        listing.reserve(2L, RESERVED_UNTIL);
        listing.markPaid(2L, RESERVED_UNTIL.minusMinutes(1));
        listing.enterInspection(RESERVED_UNTIL, RESERVED_UNTIL.plusDays(7));

        assertThatThrownBy(() -> listing.confirm(3L, RESERVED_UNTIL.plusDays(1)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_RESERVATION_MISMATCH));
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.INSPECTING);
    }

    @Test
    void confirmAllowsNullBuyerIdForAutoConfirm() {
        Listing listing = onSaleListing();
        listing.reserve(2L, RESERVED_UNTIL);
        listing.markPaid(2L, RESERVED_UNTIL.minusMinutes(1));
        listing.enterInspection(RESERVED_UNTIL, RESERVED_UNTIL.plusDays(7));

        listing.confirm(null, RESERVED_UNTIL.plusDays(1));

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.CONFIRMED);
    }

    @Test
    void settleRequiresConfirmedListing() {
        Listing listing = onSaleListing();
        listing.reserve(2L, RESERVED_UNTIL);
        listing.markPaid(2L, RESERVED_UNTIL.minusMinutes(1));
        listing.enterInspection(RESERVED_UNTIL, RESERVED_UNTIL.plusDays(7));

        assertThatThrownBy(listing::settle)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_CONFIRMED));

        listing.confirm(2L, RESERVED_UNTIL.plusDays(1));
        listing.settle();

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SETTLED);
        assertThat(listing.getSettledAt()).isNotNull();
    }

    @Test
    void publishRequiresDraftWithCompletedPrecheck() {
        Listing listing = draftListing();

        assertThatThrownBy(listing::publish)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.REQUIRED_EVIDENCE_INCOMPLETE));

        listing.completePrecheck();
        listing.publish();

        assertThat(listing.isPrecheckCompleted()).isTrue();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ON_SALE);
    }

    @Test
    void publishRejectsListingThatIsNoLongerDraft() {
        Listing listing = onSaleListing();
        listing.completePrecheck();

        assertThatThrownBy(listing::publish)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION));
    }

    @Test
    void updateDraftStepAcceptsOnlyStepsWithinRange() {
        Listing listing = draftListing();

        listing.updateDraftStep(1);
        assertThat(listing.getDraftStep()).isEqualTo(1);

        listing.updateDraftStep(4);
        assertThat(listing.getDraftStep()).isEqualTo(4);

        assertThatThrownBy(() -> listing.updateDraftStep(0))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> listing.updateDraftStep(5))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThat(listing.getDraftStep()).isEqualTo(4);
    }

    @Test
    void updateDraftStepRejectsListingThatLeftDraft() {
        Listing listing = onSaleListing();

        assertThatThrownBy(() -> listing.updateDraftStep(2))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PRODUCT_EDIT_NOT_ALLOWED));
    }

    @Test
    void updateDraftKeepsExistingTextWhenNewValueIsNull() {
        Listing listing = draftListing();

        listing.updateDraft(null, null, 700_000);

        assertThat(listing.getTitle()).isEqualTo("갤럭시 S24");
        assertThat(listing.getDescription()).isEqualTo("설명");
        assertThat(listing.getPrice()).isEqualTo(700_000);

        listing.updateDraft("갤럭시 S24 울트라", "새 설명", 800_000);

        assertThat(listing.getTitle()).isEqualTo("갤럭시 S24 울트라");
        assertThat(listing.getDescription()).isEqualTo("새 설명");
        assertThat(listing.getPrice()).isEqualTo(800_000);
    }

    @Test
    void updateBySellerLeavesUnspecifiedFieldsUntouched() {
        Category category = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 0);
        Listing listing = Listing.createDraft(
                1L, category, "갤럭시 S24", "설명", 650_000, "블랙", 256, "서울 강남구", 10L);

        listing.updateBySeller(null, null, false, null, null, false, null, false, null);

        assertThat(listing.getTitle()).isEqualTo("갤럭시 S24");
        assertThat(listing.getDescription()).isEqualTo("설명");
        assertThat(listing.getPrice()).isEqualTo(650_000);
        assertThat(listing.getColor()).isEqualTo("블랙");
        assertThat(listing.getStorageGb()).isEqualTo(256);
        assertThat(listing.getTradeRegion()).isEqualTo("서울 강남구");
    }

    @Test
    void updateBySellerClearsOptionalFieldsWhenExplicitlySpecifiedAsNull() {
        Category category = Category.createTopLevel("스마트폰", DeviceType.SMARTPHONE, 0);
        Listing listing = Listing.createDraft(
                1L, category, "갤럭시 S24", "설명", 650_000, "블랙", 256, "서울 강남구", 10L);

        listing.updateBySeller(
                "갤럭시 S24 울트라", null, true, 700_000L, null, true, null, true, "부산 해운대구");

        assertThat(listing.getTitle()).isEqualTo("갤럭시 S24 울트라");
        assertThat(listing.getDescription()).isNull();
        assertThat(listing.getPrice()).isEqualTo(700_000);
        assertThat(listing.getColor()).isNull();
        assertThat(listing.getStorageGb()).isNull();
        assertThat(listing.getTradeRegion()).isEqualTo("부산 해운대구");
    }

    @Test
    void updateBySellerIsAllowedWhileListingIsOnSaleOrHidden() {
        Listing onSale = onSaleListing();
        Listing hidden = listingWithStatus(ListingStatus.HIDDEN);

        onSale.updateBySeller("판매 중 수정", null, false, 660_000L, null, false, null, false, null);
        hidden.updateBySeller("숨김 수정", null, false, 670_000L, null, false, null, false, null);

        assertThat(onSale.getTitle()).isEqualTo("판매 중 수정");
        assertThat(onSale.getPrice()).isEqualTo(660_000);
        assertThat(hidden.getTitle()).isEqualTo("숨김 수정");
        assertThat(hidden.getPrice()).isEqualTo(670_000);
    }

    @Test
    void updateBySellerRejectsListingReservedOrLater() {
        Listing reserved = listingWithStatus(ListingStatus.RESERVED);
        Listing sold = listingWithStatus(ListingStatus.SOLD);

        assertThatThrownBy(
                        () -> reserved.updateBySeller(
                                "수정", null, false, 1L, null, false, null, false, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PRODUCT_EDIT_NOT_ALLOWED));
        assertThatThrownBy(
                        () -> sold.updateBySeller(
                                "수정", null, false, 1L, null, false, null, false, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PRODUCT_EDIT_NOT_ALLOWED));
        assertThat(reserved.getTitle()).isEqualTo("갤럭시 S24");
    }

    @Test
    void hideMovesOnSaleListingToHiddenOnly() {
        Listing listing = onSaleListing();

        listing.hide();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.HIDDEN);

        assertThatThrownBy(listing::hide)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION));
    }

    @Test
    void markSoldBySellerIsAllowedFromOnSaleAndHidden() {
        Listing onSale = onSaleListing();
        Listing hidden = listingWithStatus(ListingStatus.HIDDEN);

        onSale.markSoldBySeller();
        hidden.markSoldBySeller();

        assertThat(onSale.getStatus()).isEqualTo(ListingStatus.SOLD);
        assertThat(hidden.getStatus()).isEqualTo(ListingStatus.SOLD);
    }

    @Test
    void markSoldBySellerRejectsDraftAndReservedListings() {
        Listing draft = draftListing();
        Listing reserved = listingWithStatus(ListingStatus.RESERVED);

        assertThatThrownBy(draft::markSoldBySeller)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION));
        assertThatThrownBy(reserved::markSoldBySeller)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION));
    }

    @Test
    void reopenSoldBySellerReturnsSelfClosedListingToOnSale() {
        Listing listing = onSaleListing();
        listing.markSoldBySeller();

        listing.reopenSoldBySeller();

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ON_SALE);
    }

    @Test
    void reopenSoldBySellerRejectsListingClosedThroughPayment() {
        Listing settled = listingWithStatus(ListingStatus.SETTLED);

        assertThatThrownBy(settled::reopenSoldBySeller)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_PRODUCT_STATUS_TRANSITION));
    }

    @Test
    void applyCustomModelTrimsValuesAndTreatsBlankAsMissing() {
        Listing listing = draftListing();

        listing.applyCustomModel("  샤오미  ", "  홍미노트 13  ");

        assertThat(listing.getCustomManufacturer()).isEqualTo("샤오미");
        assertThat(listing.getCustomModelName()).isEqualTo("홍미노트 13");
        assertThat(listing.hasCustomModel()).isTrue();

        listing.applyCustomModel("   ", null);

        assertThat(listing.getCustomManufacturer()).isNull();
        assertThat(listing.getCustomModelName()).isNull();
        assertThat(listing.hasCustomModel()).isFalse();
    }

    @Test
    void hasCustomModelRequiresBothManufacturerAndModelName() {
        Listing onlyManufacturer = draftListing();
        Listing onlyModelName = draftListing();

        onlyManufacturer.applyCustomModel("샤오미", "  ");
        onlyModelName.applyCustomModel(null, "홍미노트 13");

        assertThat(onlyManufacturer.hasCustomModel()).isFalse();
        assertThat(onlyModelName.hasCustomModel()).isFalse();
    }

    @Test
    void updateWebDeviceCheckResultsCopiesGivenResultsAndAcceptsNull() {
        Listing listing = draftListing();
        Map<TestType, DeviceCheckResult> results = new LinkedHashMap<>();
        results.put(TestType.CAMERA, DeviceCheckResult.SUCCESS);

        listing.updateWebDeviceCheckResults(results);
        results.put(TestType.MICROPHONE, DeviceCheckResult.FAILED);

        assertThat(listing.getWebDeviceCheckResults())
                .containsExactly(Map.entry(TestType.CAMERA, DeviceCheckResult.SUCCESS));

        listing.updateWebDeviceCheckResults(null);

        assertThat(listing.getWebDeviceCheckResults()).isNull();
    }

    @Test
    void warningKeepsProductPublicUntilSellerAcknowledgesIt() {
        Listing listing = onSaleListing();

        listing.issueModerationWarning("상품 설명을 확인해 주세요.");

        assertThat(listing.getModerationStatus())
                .isEqualTo(ListingModerationStatus.WARNING_ACK_REQUIRED);
        assertThat(listing.isPubliclyVisible()).isTrue();

        listing.acknowledgeModerationWarning();

        assertThat(listing.getModerationStatus()).isEqualTo(ListingModerationStatus.NORMAL);
        assertThat(listing.isPubliclyVisible()).isTrue();
        assertThat(listing.getSuspendedReason()).isNull();
    }

    @Test
    void suspendedProductNeedsSellerRequestAndAdminApprovalBeforeItIsPublic() {
        Listing listing = onSaleListing();

        listing.suspendForModeration("금지된 문구를 수정해 주세요.");

        assertThat(listing.getModerationStatus()).isEqualTo(ListingModerationStatus.SUSPENDED);
        assertThat(listing.isPubliclyVisible()).isFalse();
        assertThatThrownBy(() -> listing.reserve(2L, RESERVED_UNTIL))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.LISTING_MODERATION_BLOCKED));

        listing.requestModerationRestoration();
        assertThat(listing.getModerationStatus())
                .isEqualTo(ListingModerationStatus.RESTORE_REQUESTED);
        assertThat(listing.isPubliclyVisible()).isFalse();

        listing.approveModerationRestoration();
        assertThat(listing.getModerationStatus()).isEqualTo(ListingModerationStatus.NORMAL);
        assertThat(listing.isPubliclyVisible()).isTrue();
    }

    @Test
    void rejectedRestorationReturnsProductToSuspendedState() {
        Listing listing = onSaleListing();
        listing.suspendForModeration("사진을 교체해 주세요.");
        listing.requestModerationRestoration();

        listing.rejectModerationRestoration("수정된 사진을 확인할 수 없습니다.");

        assertThat(listing.getModerationStatus()).isEqualTo(ListingModerationStatus.SUSPENDED);
        assertThat(listing.getSuspendedReason()).isEqualTo("수정된 사진을 확인할 수 없습니다.");
        assertThat(listing.isPubliclyVisible()).isFalse();
    }
}
