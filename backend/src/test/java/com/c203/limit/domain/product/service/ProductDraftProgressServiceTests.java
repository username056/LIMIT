package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import com.c203.limit.domain.inspection.enums.DeviceCheckResult;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.product.dto.request.UpdateProductDraftProgressRequest;
import com.c203.limit.domain.product.dto.request.UpdateProductDraftProgressRequest.ChecklistItemResult;
import com.c203.limit.domain.product.dto.response.ProductDraftProgressResponse;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductDraftProgressServiceTests {
    private static final Long SELLER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;

    private ListingRepository listings;
    private ListingChecklistItemRepository checklistItems;
    private ProductDraftProgressService service;
    private Listing listing;

    @BeforeEach
    void setUp() {
        listings = mock(ListingRepository.class);
        checklistItems = mock(ListingChecklistItemRepository.class);
        service = new ProductDraftProgressService(listings, checklistItems);

        listing = Listing.createDraft(SELLER_ID, null, "제목", "설명", 100_000L, null);
        when(listings.findByIdAndSellerIdAndDeletedAtIsNull(PRODUCT_ID, SELLER_ID))
                .thenReturn(Optional.of(listing));
    }

    private ListingChecklistItem confirmationItem(long id) {
        ChecklistTemplateItem templateItem = ChecklistTemplateItem.create(
                null, "LAP-KBD-005", "키보드", "작동 확인", "안내", EvidenceType.SELLER_CONFIRMATION,
                AutomationType.NONE, true, 1);
        ListingChecklistItem item =
                ListingChecklistItem.createFromTemplateItem(PRODUCT_ID, templateItem);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    @Test
    void successResultCompletesTheItem() {
        ListingChecklistItem item = confirmationItem(11L);
        when(checklistItems.findByListingIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(item));

        ProductDraftProgressResponse response = service.update(
                SELLER_ID,
                PRODUCT_ID,
                new UpdateProductDraftProgressRequest(
                        2, List.of(new ChecklistItemResult(11L, DeviceCheckResult.SUCCESS))));

        assertThat(item.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.COMPLETED);
        assertThat(item.getDeviceCheckResult()).isEqualTo(DeviceCheckResult.SUCCESS);
        assertThat(response.results()).containsEntry(11L, DeviceCheckResult.SUCCESS);
    }

    @Test
    void failedResultIsNotStoredAsCompleted() {
        ListingChecklistItem item = confirmationItem(12L);
        when(checklistItems.findByListingIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(item));

        service.update(
                SELLER_ID,
                PRODUCT_ID,
                new UpdateProductDraftProgressRequest(
                        2, List.of(new ChecklistItemResult(12L, DeviceCheckResult.FAILED))));

        assertThat(item.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.SUBMITTED);
        assertThat(item.getDeviceCheckResult()).isEqualTo(DeviceCheckResult.FAILED);
    }

    @Test
    void previousSuccessIsRevertedByANewFailedResult() {
        ListingChecklistItem item = confirmationItem(13L);
        item.applyDeviceCheckResult(DeviceCheckResult.SUCCESS);
        when(checklistItems.findByListingIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(item));

        service.update(
                SELLER_ID,
                PRODUCT_ID,
                new UpdateProductDraftProgressRequest(
                        2, List.of(new ChecklistItemResult(13L, DeviceCheckResult.FAILED))));

        assertThat(item.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.SUBMITTED);
        assertThat(item.getDeviceCheckResult()).isEqualTo(DeviceCheckResult.FAILED);
    }

    @Test
    void itemsMissingFromTheRequestAreResetToPending() {
        ListingChecklistItem item = confirmationItem(14L);
        item.applyDeviceCheckResult(DeviceCheckResult.SUCCESS);
        when(checklistItems.findByListingIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(item));

        service.update(SELLER_ID, PRODUCT_ID, new UpdateProductDraftProgressRequest(2, List.of()));

        assertThat(item.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.PENDING);
        assertThat(item.getDeviceCheckResult()).isNull();
    }

    @Test
    void itemNotBelongingToTheListingIsRejected() {
        when(checklistItems.findByListingIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(confirmationItem(15L)));

        assertThatThrownBy(() -> service.update(
                        SELLER_ID,
                        PRODUCT_ID,
                        new UpdateProductDraftProgressRequest(
                                2, List.of(new ChecklistItemResult(999L, DeviceCheckResult.SUCCESS)))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    void duplicateItemIdsInTheRequestAreRejected() {
        when(checklistItems.findByListingIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(confirmationItem(16L)));

        assertThatThrownBy(() -> service.update(
                        SELLER_ID,
                        PRODUCT_ID,
                        new UpdateProductDraftProgressRequest(
                                2,
                                List.of(
                                        new ChecklistItemResult(16L, DeviceCheckResult.SUCCESS),
                                        new ChecklistItemResult(16L, DeviceCheckResult.FAILED)))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
