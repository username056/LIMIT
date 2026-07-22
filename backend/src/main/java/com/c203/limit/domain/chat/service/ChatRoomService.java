package com.c203.limit.domain.chat.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import com.c203.limit.domain.chat.dto.response.ChatRoomResponse;
import com.c203.limit.domain.chat.entity.ChatRoom;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.chat.repository.ListingChatReader;
import com.c203.limit.domain.chat.repository.ListingChatReader.ListingChatInfo;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;

@Service
public class ChatRoomService {
    private static final String CHAT_CREATABLE_LISTING_STATUS = "ON_SALE";

    private final ListingChatReader listingReader;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomCreator creator;

    public ChatRoomService(ListingChatReader listingReader, ChatRoomRepository chatRoomRepository,
            ChatRoomCreator creator) {
        this.listingReader = listingReader;
        this.chatRoomRepository = chatRoomRepository;
        this.creator = creator;
    }

    public ChatRoomCreateResult createOrGet(Long listingId, Long buyerId) {
        ListingChatInfo listing = listingReader.findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));

        return chatRoomRepository.findByListingIdAndBuyerIdAndSellerId(
                        listingId, buyerId, listing.sellerId())
                .map(room -> ChatRoomCreateResult.existing(ChatRoomResponse.from(room)))
                .orElseGet(() -> create(listing, buyerId));
    }

    private ChatRoomCreateResult create(ListingChatInfo listing, Long buyerId) {
        validateCreation(listing, buyerId);
        try {
            ChatRoom room = creator.create(listing.listingId(), buyerId, listing.sellerId());
            return ChatRoomCreateResult.created(ChatRoomResponse.from(room));
        } catch (DataIntegrityViolationException exception) {
            ChatRoom room = chatRoomRepository.findByListingIdAndBuyerIdAndSellerId(
                            listing.listingId(), buyerId, listing.sellerId())
                    .orElseThrow(() -> exception);
            return ChatRoomCreateResult.existing(ChatRoomResponse.from(room));
        }
    }

    private void validateCreation(ListingChatInfo listing, Long buyerId) {
        if (buyerId.equals(listing.sellerId())) {
            throw new BusinessException(ErrorCode.SELF_CHAT_NOT_ALLOWED);
        }
        if (!CHAT_CREATABLE_LISTING_STATUS.equals(listing.status())) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CREATION_NOT_ALLOWED);
        }
    }
}
