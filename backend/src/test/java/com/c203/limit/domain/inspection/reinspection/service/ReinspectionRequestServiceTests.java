package com.c203.limit.domain.inspection.reinspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.entity.ReinspectionRequest;
import com.c203.limit.domain.inspection.entity.ReinspectionRequestItem;
import com.c203.limit.domain.inspection.reinspection.dto.request.ReinspectionRequestCreateRequest;
import com.c203.limit.domain.inspection.reinspection.dto.request.ReinspectionRequestItemCreateRequest;
import com.c203.limit.domain.inspection.reinspection.dto.response.ReinspectionRequestResponse;
import com.c203.limit.domain.inspection.enums.ReinspectionStatus;
import com.c203.limit.domain.inspection.reinspection.event.ReinspectionNotificationEvent;
import com.c203.limit.domain.inspection.reinspection.repository.ReinspectionRequestItemRepository;
import com.c203.limit.domain.inspection.reinspection.repository.ReinspectionRequestRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.domain.chat.service.ChatRoomService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ReinspectionRequestServiceTests {

    private static final Long LISTING_ID = 10L;
    private static final Long CHAT_ROOM_ID = 25L;
    private static final Long BUYER_ID = 100L;
    private static final Long SELLER_ID = 200L;
    private static final Long OTHER_MEMBER_ID = 999L;
    private static final Long CHECKLIST_ITEM_ID = 301L;
    private static final String REQUEST_KEY = "b3b1c7a2-3c1a-4b1a-9c1a-1a2b3c4d5e6f";

    @Mock ReinspectionRequestRepository reinspectionRequestRepository;
    @Mock ReinspectionRequestItemRepository reinspectionRequestItemRepository;
    @Mock ListingChecklistItemRepository listingChecklistItemRepository;
    @Mock ListingOwnerReader listingOwnerReader;
    @Mock ChatRoomService chatRoomService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ListingChecklistItem listingChecklistItem;

    ReinspectionRequestService service;

    @BeforeEach
    void setUp() {
        service =
                new ReinspectionRequestService(
                        reinspectionRequestRepository,
                        reinspectionRequestItemRepository,
                        listingChecklistItemRepository,
                        listingOwnerReader,
                        chatRoomService,
                        eventPublisher);
    }

    private ReinspectionRequestCreateRequest createRequest() {
        return new ReinspectionRequestCreateRequest(
                "제품 상태를 조금 더 자세히 확인하고 싶습니다.",
                List.of(new ReinspectionRequestItemCreateRequest(CHECKLIST_ITEM_ID, "모서리를 가까이 촬영해 주세요.")));
    }

    @Test
    void createsReinspectionRequestAndPublishesEvent() {
        when(listingOwnerReader.findById(LISTING_ID))
                .thenReturn(Optional.of(new ListingOwnerReader.ListingOwnerInfo(LISTING_ID, SELLER_ID)));
        when(chatRoomService.getOrCreateChatRoom(LISTING_ID, BUYER_ID, SELLER_ID)).thenReturn(CHAT_ROOM_ID);
        when(listingChecklistItemRepository.findByIdAndListingId(CHECKLIST_ITEM_ID, LISTING_ID))
                .thenReturn(Optional.of(listingChecklistItem));
        when(listingChecklistItem.getId()).thenReturn(CHECKLIST_ITEM_ID);
        when(listingChecklistItem.getName()).thenReturn("제품 외관");
        when(reinspectionRequestRepository.save(any(ReinspectionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reinspectionRequestItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reinspectionRequestRepository.countByChatRoomIdAndStatus(CHAT_ROOM_ID, ReinspectionStatus.REQUESTED))
                .thenReturn(1L);

        ReinspectionRequestResponse response = service.request(LISTING_ID, BUYER_ID, createRequest());

        assertThat(response.getListingId()).isEqualTo(LISTING_ID);
        assertThat(response.getChatRoomId()).isEqualTo(CHAT_ROOM_ID);
        assertThat(response.getBuyerId()).isEqualTo(BUYER_ID);
        assertThat(response.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(response.getStatus()).isEqualTo("REQUESTED");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getItemName()).isEqualTo("제품 외관");
        assertThat(response.getItems().get(0).getDisplayOrder()).isEqualTo(1);

        verify(eventPublisher)
                .publishEvent(
                        argThat(
                                (ReinspectionNotificationEvent event) ->
                                        event.actorId().equals(BUYER_ID)
                                                && event.recipientId().equals(SELLER_ID)
                                                && event.pendingRequestCount() == 1L));
    }

    @Test
    void throwsSelfRequestNotAllowedWhenBuyerIsSeller() {
        when(listingOwnerReader.findById(LISTING_ID))
                .thenReturn(Optional.of(new ListingOwnerReader.ListingOwnerInfo(LISTING_ID, BUYER_ID)));

        assertThatThrownBy(() -> service.request(LISTING_ID, BUYER_ID, createRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.REINSPECTION_SELF_REQUEST_NOT_ALLOWED));
        verifyNoInteractions(chatRoomService, reinspectionRequestRepository, eventPublisher);
    }

    @Test
    void throwsProductNotFoundWhenListingDoesNotExist() {
        when(listingOwnerReader.findById(LISTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(LISTING_ID, BUYER_ID, createRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
        verifyNoInteractions(chatRoomService, reinspectionRequestRepository, eventPublisher);
    }

    @Test
    void throwsItemListingMismatchWhenChecklistItemNotInListing() {
        when(listingOwnerReader.findById(LISTING_ID))
                .thenReturn(Optional.of(new ListingOwnerReader.ListingOwnerInfo(LISTING_ID, SELLER_ID)));
        when(chatRoomService.getOrCreateChatRoom(LISTING_ID, BUYER_ID, SELLER_ID)).thenReturn(CHAT_ROOM_ID);
        when(listingChecklistItemRepository.findByIdAndListingId(CHECKLIST_ITEM_ID, LISTING_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(LISTING_ID, BUYER_ID, createRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.REINSPECTION_ITEM_LISTING_MISMATCH));
        verifyNoInteractions(reinspectionRequestRepository, eventPublisher);
    }

    @Test
    void completesRequestedRequestAndPublishesEvent() {
        ReinspectionRequest reinspectionRequest =
                ReinspectionRequest.request(LISTING_ID, CHAT_ROOM_ID, REQUEST_KEY, "사유", BUYER_ID, SELLER_ID);
        when(reinspectionRequestRepository.findByRequestKey(REQUEST_KEY))
                .thenReturn(Optional.of(reinspectionRequest));
        when(reinspectionRequestItemRepository.findByReinspectionRequestIdOrderByDisplayOrderAsc(
                        reinspectionRequest.getId()))
                .thenReturn(List.of());
        when(reinspectionRequestRepository.countByChatRoomIdAndStatus(CHAT_ROOM_ID, ReinspectionStatus.REQUESTED))
                .thenReturn(0L);

        ReinspectionRequestResponse response = service.complete(REQUEST_KEY, SELLER_ID);

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        verify(eventPublisher)
                .publishEvent(
                        argThat(
                                (ReinspectionNotificationEvent event) ->
                                        event.actorId().equals(SELLER_ID)
                                                && event.recipientId().equals(BUYER_ID)
                                                && event.pendingRequestCount() == 0L));
    }

    @Test
    void throwsRequestNotFoundWhenRequestKeyUnknown() {
        when(reinspectionRequestRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(REQUEST_KEY, SELLER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.REINSPECTION_REQUEST_NOT_FOUND));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void throwsAccessDeniedWhenCallerIsNotSeller() {
        ReinspectionRequest reinspectionRequest =
                ReinspectionRequest.request(LISTING_ID, CHAT_ROOM_ID, REQUEST_KEY, "사유", BUYER_ID, SELLER_ID);
        when(reinspectionRequestRepository.findByRequestKey(REQUEST_KEY))
                .thenReturn(Optional.of(reinspectionRequest));

        assertThatThrownBy(() -> service.complete(REQUEST_KEY, OTHER_MEMBER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REINSPECTION_ACCESS_DENIED));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void throwsAlreadyProcessedWhenStatusIsNotRequested() {
        ReinspectionRequest reinspectionRequest =
                ReinspectionRequest.request(LISTING_ID, CHAT_ROOM_ID, REQUEST_KEY, "사유", BUYER_ID, SELLER_ID);
        reinspectionRequest.cancel();
        when(reinspectionRequestRepository.findByRequestKey(REQUEST_KEY))
                .thenReturn(Optional.of(reinspectionRequest));

        assertThatThrownBy(() -> service.complete(REQUEST_KEY, SELLER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.REINSPECTION_ALREADY_PROCESSED));
        verify(reinspectionRequestItemRepository, never()).findByReinspectionRequestIdOrderByDisplayOrderAsc(any());
        verifyNoInteractions(eventPublisher);
    }
}
