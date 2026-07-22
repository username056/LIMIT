package com.c203.limit.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.c203.limit.domain.chat.entity.ChatRoom;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.chat.repository.ListingChatReader;
import com.c203.limit.domain.chat.repository.ListingChatReader.ListingChatInfo;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;

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

    private ChatRoom room(Long id) {
        ChatRoom room = ChatRoom.create(LISTING_ID, BUYER_ID, SELLER_ID);
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }
}
