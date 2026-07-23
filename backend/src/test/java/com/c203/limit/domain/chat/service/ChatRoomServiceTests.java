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
import java.util.UUID;

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
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.domain.chat.repository.ChatMessageProjection;
import com.c203.limit.domain.chat.repository.ChatMessageRepository;
import com.c203.limit.domain.chat.repository.ChatRoomSummaryProjection;
import com.c203.limit.domain.chat.repository.ListingChatReader;
import com.c203.limit.domain.chat.repository.ListingChatReader.ListingChatInfo;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.CursorResponse;
import com.c203.limit.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.c203.limit.domain.chat.domain.MessageStatus;
import com.c203.limit.domain.chat.domain.MessageType;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTests {
    private static final Long LISTING_ID = 10L;
    private static final Long BUYER_ID = 20L;
    private static final Long SELLER_ID = 30L;

    @Mock ListingChatReader listingReader;
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatRoomParticipantRepository participantRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock ChatRoomCreator creator;
    ChatRoomService service;

    @BeforeEach
    void setUp() {
        service = new ChatRoomService(
                listingReader, chatRoomRepository, participantRepository, chatMessageRepository, creator);
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

    @Test
    void returnsOlderMessagesWithSequenceCursor() {
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(100L, BUYER_ID)).thenReturn(true);
        when(chatMessageRepository.findBeforeSequence(eq(100L), eq(10L), any(Pageable.class)))
                .thenReturn(List.of(message(9L), message(8L), message(7L)));

        CursorResponse<com.c203.limit.domain.chat.dto.response.ChatMessageResponse> result =
                service.findMessages(100L, BUYER_ID, 10L, null, 2);

        assertThat(result.content().stream().map(message -> message.roomSequence()).toList())
                .containsExactly(9L, 8L);
        assertThat(result.nextCursor()).isEqualTo("8");
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void returnsMissingMessagesAfterLastReceivedSequence() {
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(100L, BUYER_ID)).thenReturn(true);
        when(chatMessageRepository.findAfterSequence(eq(100L), eq(7L), any(Pageable.class)))
                .thenReturn(List.of(message(8L), message(9L)));

        CursorResponse<com.c203.limit.domain.chat.dto.response.ChatMessageResponse> result =
                service.findMessages(100L, BUYER_ID, null, 7L, 10);

        assertThat(result.content().stream().map(message -> message.roomSequence()).toList())
                .containsExactly(8L, 9L);
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void rejectsUsingBeforeAndAfterSequenceTogether() {
        assertThatThrownBy(() -> service.findMessages(100L, BUYER_ID, 10L, 5L, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(participantRepository, chatMessageRepository);
    }

    @Test
    void rejectsMessageLookupByNonParticipant() {
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(100L, BUYER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.findMessages(100L, BUYER_ID, null, null, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
        verifyNoInteractions(chatMessageRepository);
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

    private ChatMessageProjection message(Long sequence) {
        return new ChatMessageProjection() {
            public Long getMessageId() { return sequence + 100L; }
            public Long getRoomSequence() { return sequence; }
            public Long getSenderId() { return SELLER_ID; }
            public UUID getClientMessageId() { return new UUID(0L, sequence); }
            public MessageType getType() { return MessageType.TEXT; }
            public String getContent() { return "message-" + sequence; }
            public MessageStatus getStatus() { return MessageStatus.SENT; }
            public LocalDateTime getSentAt() { return LocalDateTime.of(2026, 7, 23, 12, 0); }
        };
    }
}
