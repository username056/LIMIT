package com.c203.limit.domain.chat.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.c203.limit.domain.chat.dto.response.ChatRoomResponse;
import com.c203.limit.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.c203.limit.domain.chat.dto.request.ChatMessageSendRequest;
import com.c203.limit.domain.chat.dto.request.ChatReadRequest;
import com.c203.limit.domain.chat.dto.response.ChatMessageResponse;
import com.c203.limit.domain.chat.entity.ChatMessage;
import com.c203.limit.domain.chat.entity.ChatRoomParticipant;
import com.c203.limit.domain.chat.domain.MessageType;
import com.c203.limit.domain.chat.entity.ChatRoom;
import com.c203.limit.domain.chat.repository.ChatMessageProjection;
import com.c203.limit.domain.chat.repository.ChatMessageRepository;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.chat.repository.ChatRoomSummaryProjection;
import com.c203.limit.domain.chat.repository.ListingChatReader;
import com.c203.limit.domain.chat.repository.ListingChatReader.ListingChatInfo;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.CursorResponse;

@Service
public class ChatRoomService {
    private static final Logger log = LoggerFactory.getLogger(ChatRoomService.class);
    private static final String CHAT_CREATABLE_LISTING_STATUS = "ON_SALE";
    private static final int MAX_PAGE_SIZE = 100;

    private final ListingChatReader listingReader;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomCreator creator;

    public ChatRoomService(ListingChatReader listingReader, ChatRoomRepository chatRoomRepository,
            ChatRoomParticipantRepository participantRepository, ChatMessageRepository chatMessageRepository,
            ChatRoomCreator creator) {
        this.listingReader = listingReader;
        this.chatRoomRepository = chatRoomRepository;
        this.participantRepository = participantRepository;
        this.chatMessageRepository = chatMessageRepository;
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

    @Transactional(readOnly = true)
    public CursorResponse<ChatRoomSummaryResponse> findRooms(Long memberId, Long cursor, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        List<ChatRoomSummaryProjection> rows = chatRoomRepository.findSummariesByMemberId(
                memberId, cursor, PageRequest.of(0, size + 1));
        boolean hasNext = rows.size() > size;
        List<ChatRoomSummaryResponse> content = rows.stream()
                .limit(size)
                .map(row -> toSummary(row, memberId))
                .toList();
        String nextCursor = hasNext ? content.get(content.size() - 1).roomId().toString() : null;
        return new CursorResponse<>(content, nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    public CursorResponse<ChatMessageResponse> findMessages(
            Long roomId, Long memberId, Long beforeSeq, Long afterSeq, int size) {
        validateMessageCursor(beforeSeq, afterSeq, size);
        if (!participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(roomId, memberId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }

        PageRequest page = PageRequest.of(0, size + 1);
        List<ChatMessageProjection> rows = afterSeq == null
                ? chatMessageRepository.findBeforeSequence(roomId, beforeSeq, page)
                : chatMessageRepository.findAfterSequence(roomId, afterSeq, page);
        boolean hasNext = rows.size() > size;
        List<ChatMessageResponse> content = rows.stream().limit(size).map(ChatMessageResponse::from).toList();
        String nextCursor = hasNext ? content.get(content.size() - 1).roomSequence().toString() : null;
        return new CursorResponse<>(content, nextCursor, hasNext);
    }

    @Transactional
    public ChatMessageSendResult sendMessage(Long roomId, Long memberId, ChatMessageSendRequest request) {
        ChatRoomParticipant participant = participant(roomId, memberId);
        if (!MessageType.TEXT.name().equals(request.type())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return chatMessageRepository.findByChatRoomIdAndClientMessageId(roomId, request.clientMessageId())
                .map(message -> new ChatMessageSendResult(ChatMessageResponse.from(message), false))
                .orElseGet(() -> saveNewTextMessage(roomId, participant.getUserId(), request, content));
    }

    @Transactional
    public Long readMessages(Long roomId, Long memberId, ChatReadRequest request) {
        ChatRoomParticipant participant = participant(roomId, memberId);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
        long readSeq = Math.min(request.lastReadSeq(), room.nextMessageSequence() - 1);
        participant.readUpTo(readSeq);
        return participant.getLastReadSeq();
    }

    private void validateMessageCursor(Long beforeSeq, Long afterSeq, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE
                || beforeSeq != null && beforeSeq < 1
                || afterSeq != null && afterSeq < 0
                || beforeSeq != null && afterSeq != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private ChatMessageSendResult saveNewTextMessage(
            Long roomId, Long senderId, ChatMessageSendRequest request, String content) {
        ChatRoom room = chatRoomRepository.findLockedById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
        var existing = chatMessageRepository.findByChatRoomIdAndClientMessageId(
                roomId, request.clientMessageId());
        if (existing.isPresent()) {
            return new ChatMessageSendResult(ChatMessageResponse.from(existing.get()), false);
        }
        long roomSequence = room.nextMessageSequence();
        ChatMessage message = chatMessageRepository.save(ChatMessage.sendText(
                roomId, roomSequence, senderId, request.clientMessageId(), content, LocalDateTime.now()));
        room.recordMessage(message.getId(), roomSequence, message.getSentAt());
        log.info(
                "chat message sent: roomId={}, senderId={}, messageId={}, roomSequence={}",
                roomId,
                senderId,
                message.getId(),
                roomSequence);
        return new ChatMessageSendResult(ChatMessageResponse.from(message), true);
    }

    private ChatRoomParticipant participant(Long roomId, Long memberId) {
        return participantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(roomId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
    }

    public record ChatMessageSendResult(ChatMessageResponse message, boolean created) {}

    private ChatRoomSummaryResponse toSummary(ChatRoomSummaryProjection row, Long memberId) {
        Long counterpartId = memberId.equals(row.getBuyerId()) ? row.getSellerId() : row.getBuyerId();
        long unreadCount = Math.max(0L, row.getLastMessageSeq() - row.getLastReadSeq());
        return new ChatRoomSummaryResponse(
                row.getRoomId(), row.getListingId(), counterpartId, row.getStatus().name(),
                row.getLastMessageId(), row.getLastMessageSeq(), row.getLastMessageAt(),
                unreadCount, row.getCreatedAt());
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
