package com.c203.limit.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.c203.limit.domain.chat.entity.ChatRoom;
import com.c203.limit.domain.chat.domain.ChatRoomStatus;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.chat.repository.ChatRoomSummaryProjection;
import com.c203.limit.domain.chat.repository.ListingChatReader;
import com.c203.limit.domain.chat.repository.ListingChatReader.ListingChatInfo;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.CursorResponse;
import com.c203.limit.domain.chat.dto.response.ChatRoomSummaryResponse;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTests {
    private static final Long LISTING_ID = 10L;
    private static final Long BUYER_ID = 20L;
    private static final Long SELLER_ID = 30L;

    @Mock ListingChatReader listingReader;
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatRoomCreator creator;
    ChatRoomService service;

    @BeforeEach
    void setUp() {
        service = new ChatRoomService(listingReader, chatRoomRepository, creator);
    }

    @Test
    void createsRoomWhenItDoesNotExist() {
        ListingChatInfo listing = new ListingChatInfo(LISTING_ID, SELLER_ID, "ON_SALE");
        ChatRoom room = room(100L);
        when(listingReader.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(chatRoomRepository.findByListingIdAndBuyerIdAndSellerId(LISTING_ID, BUYER_ID, SELLER_ID))
                .thenReturn(Optional.empty());
        when(creator.create(LISTING_ID, BUYER_ID, SELLER_ID)).thenReturn(room);

        ChatRoomCreateResult result = service.createOrGet(LISTING_ID, BUYER_ID);

        assertThat(result.created()).isTrue();
        assertThat(result.response().roomId()).isEqualTo(100L);
    }

    @Test
    void returnsExistingRoomWithoutCreatingAnotherOne() {
        ListingChatInfo listing = new ListingChatInfo(LISTING_ID, SELLER_ID, "PAID");
        ChatRoom room = room(100L);
        when(listingReader.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(chatRoomRepository.findByListingIdAndBuyerIdAndSellerId(LISTING_ID, BUYER_ID, SELLER_ID))
                .thenReturn(Optional.of(room));

        ChatRoomCreateResult result = service.createOrGet(LISTING_ID, BUYER_ID);

        assertThat(result.created()).isFalse();
        verifyNoInteractions(creator);
    }

    @Test
    void returnsRoomCreatedByConcurrentRequest() {
        ListingChatInfo listing = new ListingChatInfo(LISTING_ID, SELLER_ID, "ON_SALE");
        ChatRoom room = room(100L);
        when(listingReader.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(chatRoomRepository.findByListingIdAndBuyerIdAndSellerId(LISTING_ID, BUYER_ID, SELLER_ID))
                .thenReturn(Optional.empty(), Optional.of(room));
        when(creator.create(LISTING_ID, BUYER_ID, SELLER_ID))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        ChatRoomCreateResult result = service.createOrGet(LISTING_ID, BUYER_ID);

        assertThat(result.created()).isFalse();
        assertThat(result.response().roomId()).isEqualTo(100L);
    }

    @Test
    void rejectsSellerChattingWithOwnListing() {
        when(listingReader.findById(LISTING_ID))
                .thenReturn(Optional.of(new ListingChatInfo(LISTING_ID, BUYER_ID, "ON_SALE")));
        when(chatRoomRepository.findByListingIdAndBuyerIdAndSellerId(LISTING_ID, BUYER_ID, BUYER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOrGet(LISTING_ID, BUYER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SELF_CHAT_NOT_ALLOWED));
        verifyNoInteractions(creator);
    }

    @Test
    void rejectsMissingListing() {
        when(listingReader.findById(LISTING_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createOrGet(LISTING_ID, BUYER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LISTING_NOT_FOUND));
        verifyNoInteractions(chatRoomRepository, creator);
    }

    @Test
    void returnsMemberRoomsWithUnreadCountAndNextCursor() {
        ChatRoomSummaryProjection first = summary(100L, BUYER_ID, SELLER_ID, 8L, 3L);
        ChatRoomSummaryProjection second = summary(90L, BUYER_ID, SELLER_ID, 4L, 4L);
        when(chatRoomRepository.findSummariesByMemberId(
                eq(BUYER_ID), isNull(), any(Pageable.class))).thenReturn(List.of(first, second));

        CursorResponse<ChatRoomSummaryResponse> result = service.findRooms(BUYER_ID, null, 1);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).counterpartId()).isEqualTo(SELLER_ID);
        assertThat(result.content().get(0).unreadCount()).isEqualTo(5L);
        assertThat(result.nextCursor()).isEqualTo("100");
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void rejectsInvalidChatRoomPageSize() {
        assertThatThrownBy(() -> service.findRooms(BUYER_ID, null, 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(chatRoomRepository);
    }

    private ChatRoomSummaryProjection summary(
            Long roomId, Long buyerId, Long sellerId, long lastMessageSeq, long lastReadSeq) {
        return new ChatRoomSummaryProjection() {
            public Long getRoomId() { return roomId; }
            public Long getListingId() { return LISTING_ID; }
            public Long getBuyerId() { return buyerId; }
            public Long getSellerId() { return sellerId; }
            public ChatRoomStatus getStatus() { return ChatRoomStatus.ACTIVE; }
            public Long getLastMessageId() { return 50L; }
            public long getLastMessageSeq() { return lastMessageSeq; }
            public LocalDateTime getLastMessageAt() { return null; }
            public long getLastReadSeq() { return lastReadSeq; }
            public LocalDateTime getCreatedAt() { return LocalDateTime.of(2026, 7, 22, 12, 0); }
        };
    }

    private ChatRoom room(Long id) {
        ChatRoom room = ChatRoom.create(LISTING_ID, BUYER_ID, SELLER_ID);
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }
}
