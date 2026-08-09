package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.agent.InspectionSessionDtos.SubmitTestResultRequest;
import com.c203.limit.domain.inspection.agent.InspectionSessionTestResult;
import com.c203.limit.domain.inspection.agent.InspectionSessionTestResultRepository;
import com.c203.limit.domain.inspection.entity.ChecklistTemplateItem;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import com.c203.limit.domain.inspection.enums.DeviceCheckResult;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.enums.InspectionUserResult;
import com.c203.limit.domain.inspection.enums.MeasurementStatus;
import com.c203.limit.domain.inspection.enums.TestType;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.product.dto.request.UpdateProductDraftProgressRequest;
import com.c203.limit.domain.product.dto.request.UpdateProductDraftProgressRequest.ChecklistItemResult;
import com.c203.limit.domain.product.dto.request.UpdateProductDraftProgressRequest.WebDeviceResult;
import com.c203.limit.domain.product.dto.response.ProductDraftProgressResponse;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductDraftProgressServiceTests {
    private static final Long SELLER_ID = 1L;
    private static final Long PRODUCT_ID = 100L;

    private ListingRepository listings;
    private ListingChecklistItemRepository checklistItems;
    private InspectionSessionTestResultRepository automaticResults;
    private ProductDraftProgressService service;
    private Listing listing;

    @BeforeEach
    void setUp() {
        listings = mock(ListingRepository.class);
        checklistItems = mock(ListingChecklistItemRepository.class);
        automaticResults = mock(InspectionSessionTestResultRepository.class);
        service = new ProductDraftProgressService(listings, checklistItems, automaticResults);

        listing = Listing.createDraft(SELLER_ID, null, "제목", "설명", 100_000L, null);
        ReflectionTestUtils.setField(listing, "id", PRODUCT_ID);
        when(listings.findByIdAndSellerIdAndDeletedAtIsNull(PRODUCT_ID, SELLER_ID))
                .thenReturn(Optional.of(listing));
        when(automaticResults.findAllByListingIdNewestFirst(PRODUCT_ID)).thenReturn(List.of());
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

    private ListingChecklistItem videoItem(long id, String itemCode) {
        ChecklistTemplateItem templateItem = ChecklistTemplateItem.create(
                null, itemCode, "영상 항목", "작동 확인", "안내", EvidenceType.VIDEO,
                AutomationType.NONE, true, 1);
        ListingChecklistItem item =
                ListingChecklistItem.createFromTemplateItem(PRODUCT_ID, templateItem);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    @Test
    void exposesAutomaticResultWithoutChecklistItem() {
        when(checklistItems.findByListingIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of());
        InspectionSessionTestResult keyboard = InspectionSessionTestResult.create(
                "session-1",
                null,
                new SubmitTestResultRequest(
                        UUID.randomUUID(),
                        TestType.KEYBOARD,
                        MeasurementStatus.DETECTED,
                        InspectionUserResult.USER_CONFIRMED,
                        Map.of(),
                        OffsetDateTime.now(),
                        null),
                1,
                LocalDateTime.now());
        when(automaticResults.findAllByListingIdNewestFirst(PRODUCT_ID))
                .thenReturn(List.of(keyboard));

        ProductDraftProgressResponse response = service.find(SELLER_ID, PRODUCT_ID);

        assertThat(response.automaticDeviceResults())
                .containsEntry(TestType.KEYBOARD, DeviceCheckResult.SUCCESS);
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

    /*
      판매 중인 상품을 고치면서 점검하는 경우다. draftStep 기록은 DRAFT에서만 허용되는데, 예전에는
      그 한 줄 때문에 409가 나면서 점검 결과까지 함께 롤백됐다. 판매자에게는 "점검 결과를 저장하지
      못했습니다"만 뜨고, 분명히 점검한 항목이 계속 미점검으로 남았다.
    */
    @Test
    void savesResultsForAListingThatIsAlreadyOnSale() {
        ListingChecklistItem item = confirmationItem(19L);
        when(checklistItems.findByListingIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(item));
        listing.completePrecheck();
        listing.publish();

        ProductDraftProgressResponse response = service.update(
                SELLER_ID,
                PRODUCT_ID,
                new UpdateProductDraftProgressRequest(
                        2, List.of(new ChecklistItemResult(19L, DeviceCheckResult.SUCCESS))));

        assertThat(item.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.COMPLETED);
        assertThat(response.results()).containsEntry(19L, DeviceCheckResult.SUCCESS);
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

    // 디스플레이·충전은 EvidenceType.VIDEO라 실제 영상 증빙으로만 완료돼야 한다. 이 경로로
    // device-check 결과를 보내 완료 처리를 우회할 수 없도록, 체크리스트 항목 목록에 없는
    // ID로 취급해 요청 자체를 거부한다.
    @Test
    void displayAndChargingVideoItemsRejectWebDeviceCheckResults() {
        ListingChecklistItem display = videoItem(21L, "LAP-DSP-003");
        ListingChecklistItem charging = videoItem(22L, "LAP-CHG-007");
        when(checklistItems.findByListingIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(display, charging));

        assertThatThrownBy(() -> service.update(
                        SELLER_ID,
                        PRODUCT_ID,
                        new UpdateProductDraftProgressRequest(
                                2,
                                List.of(
                                        new ChecklistItemResult(21L, DeviceCheckResult.SUCCESS),
                                        new ChecklistItemResult(22L, DeviceCheckResult.SUCCESS)))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);

        assertThat(display.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.PENDING);
        assertThat(charging.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.PENDING);
    }

    @Test
    void omittingVideoItemFromWebResultsDoesNotEraseExistingEvidenceCompletion() {
        ListingChecklistItem display = videoItem(23L, "LAP-DSP-003");
        display.markCompleted();
        when(checklistItems.findByListingIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of(display));

        service.update(SELLER_ID, PRODUCT_ID, new UpdateProductDraftProgressRequest(2, List.of()));

        assertThat(display.getCompletionStatus()).isEqualTo(ChecklistItemCompletionStatus.COMPLETED);
    }

    @Test
    void storesSevenWebChecksWithoutChecklistItems() {
        when(checklistItems.findByListingIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(List.of());

        ProductDraftProgressResponse response = service.update(
                SELLER_ID,
                PRODUCT_ID,
                new UpdateProductDraftProgressRequest(
                        2,
                        List.of(),
                        List.of(
                                new WebDeviceResult(TestType.SPEAKER, DeviceCheckResult.SUCCESS),
                                new WebDeviceResult(TestType.DISPLAY, DeviceCheckResult.SUCCESS),
                                new WebDeviceResult(TestType.CHARGING, DeviceCheckResult.SUCCESS),
                                new WebDeviceResult(TestType.CAMERA, DeviceCheckResult.SUCCESS),
                                new WebDeviceResult(TestType.MICROPHONE, DeviceCheckResult.SUCCESS),
                                new WebDeviceResult(TestType.KEYBOARD, DeviceCheckResult.SUCCESS),
                                new WebDeviceResult(TestType.TOUCHPAD, DeviceCheckResult.SUCCESS))));

        assertThat(response.deviceResults()).containsAllEntriesOf(Map.of(
                TestType.SPEAKER, DeviceCheckResult.SUCCESS,
                TestType.DISPLAY, DeviceCheckResult.SUCCESS,
                TestType.CHARGING, DeviceCheckResult.SUCCESS,
                TestType.CAMERA, DeviceCheckResult.SUCCESS,
                TestType.MICROPHONE, DeviceCheckResult.SUCCESS,
                TestType.KEYBOARD, DeviceCheckResult.SUCCESS,
                TestType.TOUCHPAD, DeviceCheckResult.SUCCESS));
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
