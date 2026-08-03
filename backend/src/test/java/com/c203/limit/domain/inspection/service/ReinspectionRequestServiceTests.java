package com.c203.limit.domain.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.c203.limit.domain.chat.entity.ChatRoom;
import com.c203.limit.domain.chat.service.ChatRoomService;
import com.c203.limit.domain.inspection.dto.request.ReinspectionRequestCreateRequest;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.entity.ReinspectionRequest;
import com.c203.limit.domain.inspection.entity.ReinspectionRequestItem;
import com.c203.limit.domain.inspection.event.ReinspectionNotificationEvent;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader.ListingOwnerInfo;
import com.c203.limit.domain.inspection.repository.ReinspectionRequestItemRepository;
import com.c203.limit.domain.inspection.repository.ReinspectionRequestRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReinspectionRequestServiceTests {
    private static final Long LISTING_ID = 10L;
    private static final Long ROOM_ID = 20L;
    private static final Long BUYER_ID = 30L;
    private static final Long SELLER_ID = 40L;

    @Mock ListingOwnerReader listingOwnerReader;
    @Mock ChatRoomService chatRoomService;
    @Mock ListingChecklistItemRepository checklistItemRepository;
    @Mock ReinspectionRequestRepository requestRepository;
    @Mock ReinspectionRequestItemRepository requestItemRepository;
    @Mock EvidenceRepository evidenceRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    ReinspectionRequestService service;

    @BeforeEach
    void setUp() {
        service = new ReinspectionRequestService(
                listingOwnerReader,
                chatRoomService,
                checklistItemRepository,
                requestRepository,
                requestItemRepository,
                evidenceRepository,
                eventPublisher);
    }

    @Test
    void createsRequestAndPublishesRequestedEvent() {
        ChatRoom room = ChatRoom.create(LISTING_ID, BUYER_ID, SELLER_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        ListingChecklistItem checklistItem = checklistItem(101L, "외관");
        when(listingOwnerReader.findById(LISTING_ID))
                .thenReturn(Optional.of(new ListingOwnerInfo(LISTING_ID, SELLER_ID)));
        when(chatRoomService.getOrCreateChatRoom(LISTING_ID, BUYER_ID, SELLER_ID))
                .thenReturn(ROOM_ID);
        when(checklistItemRepository.findAllByIdInAndListingId(List.of(101L), LISTING_ID))
                .thenReturn(List.of(checklistItem));
        when(requestRepository.save(any(ReinspectionRequest.class)))
                .thenAnswer(invocation -> {
                    ReinspectionRequest request = invocation.getArgument(0);
                    ReflectionTestUtils.setField(request, "id", 501L);
                    return request;
                });
        when(requestItemRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(requestRepository.countByChatRoomIdAndStatus(
                        ROOM_ID, com.c203.limit.domain.inspection.enums.ReinspectionStatus.REQUESTED))
                .thenReturn(1L);

        var response = service.create(
                LISTING_ID,
                BUYER_ID,
                new ReinspectionRequestCreateRequest(
                        "흠집을 다시 보여 주세요",
                        List.of(new ReinspectionRequestCreateRequest.Item(
                                101L, "좌측 모서리 확대"))));

        assertThat(response.status()).isEqualTo("REQUESTED");
        assertThat(response.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.checklistItemId()).isEqualTo(101L);
                    assertThat(item.requestContent()).isEqualTo("좌측 모서리 확대");
                });
        ArgumentCaptor<ReinspectionNotificationEvent> captor =
                ArgumentCaptor.forClass(ReinspectionNotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type())
                .isEqualTo(ReinspectionNotificationEvent.Type.REQUESTED);
        assertThat(captor.getValue().recipientId()).isEqualTo(SELLER_ID);
        assertThat(captor.getValue().pendingRequestCount()).isEqualTo(1);
    }

    @Test
    void rejectsChecklistItemFromAnotherListing() {
        ChatRoom room = ChatRoom.create(LISTING_ID, BUYER_ID, SELLER_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        when(listingOwnerReader.findById(LISTING_ID))
                .thenReturn(Optional.of(new ListingOwnerInfo(LISTING_ID, SELLER_ID)));
        when(chatRoomService.getOrCreateChatRoom(LISTING_ID, BUYER_ID, SELLER_ID))
                .thenReturn(ROOM_ID);
        when(checklistItemRepository.findAllByIdInAndListingId(List.of(101L), LISTING_ID))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.create(
                        LISTING_ID,
                        BUYER_ID,
                        new ReinspectionRequestCreateRequest(
                                "다시 확인",
                                List.of(new ReinspectionRequestCreateRequest.Item(
                                        101L, "다른 매물 항목")))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REINSPECTION_ITEM_NOT_FOUND));
        verifyNoInteractions(requestRepository, eventPublisher);
    }

    @Test
    void findsRequestsWhereCurrentMemberIsSellerOrBuyer() {
        ReinspectionRequest request = ReinspectionRequest.request(
                LISTING_ID, ROOM_ID, "request-key", "다시 검수", BUYER_ID, SELLER_ID);
        ReflectionTestUtils.setField(request, "id", 501L);
        when(requestRepository.findBySellerIdOrBuyerIdOrderByRequestedAtDesc(
                        BUYER_ID, BUYER_ID))
                .thenReturn(List.of(request));
        when(requestItemRepository.findByReinspectionRequestIdOrderByDisplayOrderAsc(501L))
                .thenReturn(List.of());

        var responses = service.findForMember(BUYER_ID);

        assertThat(responses).singleElement()
                .satisfies(response -> {
                    assertThat(response.requestKey()).isEqualTo("request-key");
                    assertThat(response.status()).isEqualTo("REQUESTED");
                });
    }

    @Test
    void completesRequestAndPublishesCompletedEventForBuyer() {
        ReinspectionRequest request = ReinspectionRequest.request(
                LISTING_ID, ROOM_ID, "request-key", "다시 확인", BUYER_ID, SELLER_ID);
        ReflectionTestUtils.setField(request, "id", 501L);
        when(requestRepository.findByRequestKey("request-key")).thenReturn(Optional.of(request));
        when(requestItemRepository.findByReinspectionRequestIdOrderByDisplayOrderAsc(501L))
                .thenReturn(List.of());

        var response = service.complete("request-key", SELLER_ID);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.completedAt()).isNotNull();
        ArgumentCaptor<ReinspectionNotificationEvent> captor =
                ArgumentCaptor.forClass(ReinspectionNotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type())
                .isEqualTo(ReinspectionNotificationEvent.Type.COMPLETED);
        assertThat(captor.getValue().recipientId()).isEqualTo(BUYER_ID);
    }

    @Test
    void rejectsCompletionByBuyer() {
        ReinspectionRequest request = ReinspectionRequest.request(
                LISTING_ID, ROOM_ID, "request-key", "다시 확인", BUYER_ID, SELLER_ID);
        when(requestRepository.findByRequestKey("request-key")).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.complete("request-key", BUYER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REINSPECTION_SELLER_REQUIRED));
        verifyNoInteractions(requestItemRepository, eventPublisher);
    }

    private ListingChecklistItem checklistItem(Long id, String name) {
        ListingChecklistItem item = mock(ListingChecklistItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getName()).thenReturn(name);
        when(item.isVisibleToBuyer()).thenReturn(true);
        return item;
    }
}
