package com.c203.limit.domain.chat.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.c203.limit.domain.chat.dto.response.ReinspectionNotificationResponse;
import com.c203.limit.domain.chat.entity.ChatMessage;
import com.c203.limit.domain.chat.entity.ChatRoomParticipant;
import com.c203.limit.domain.chat.entity.ChatMedia;
import com.c203.limit.domain.chat.entity.ChatMessageMedia;
import com.c203.limit.domain.chat.domain.MessageType;
import com.c203.limit.domain.chat.domain.UploadStatus;
import com.c203.limit.domain.chat.entity.ChatRoom;
import com.c203.limit.domain.chat.repository.ChatMessageProjection;
import com.c203.limit.domain.chat.repository.ChatMessageRepository;
import com.c203.limit.domain.chat.repository.ChatOutboxEventRepository;
import com.c203.limit.domain.chat.repository.ChatMediaRepository;
import com.c203.limit.domain.chat.repository.ChatMessageMediaRepository;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.domain.chat.repository.ChatRoomContextReader;
import com.c203.limit.domain.chat.repository.ChatRoomContextReader.ChatRoomContext;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.chat.repository.ChatRoomSummaryProjection;
import com.c203.limit.domain.chat.repository.ListingChatReader;
import com.c203.limit.domain.chat.repository.ListingChatReader.ListingChatInfo;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.CursorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ChatRoomService {
    private static final Logger log = LoggerFactory.getLogger(ChatRoomService.class);
    private static final String CHAT_CREATABLE_LISTING_STATUS = "ON_SALE";
    // 결제까지 마친 구매자는 판매자에게 계속 문의할 수 있어야 한다 — ON_SALE만 허용하면 결제 완료
    // 후 처음 문의하는 구매자가 CHAT_ROOM_CREATION_NOT_ALLOWED로 막힌다. 그 외 상태(취소·숨김 등)는
    // 여전히 막고, 이 상태에서도 실제 구매자 본인일 때만 허용한다(관계없는 제3자는 계속 거부).
    private static final Set<String> POST_PURCHASE_CHAT_ALLOWED_LISTING_STATUS =
            Set.of("PAID", "INSPECTING", "CONFIRMED", "SETTLED");
    private static final int MAX_PAGE_SIZE = 100;

    private final ListingChatReader listingReader;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMediaRepository chatMediaRepository;
    private final ChatMessageMediaRepository chatMessageMediaRepository;
    private final ChatRoomContextReader contextReader;
    private final ChatRoomCreator creator;
    private final ChatOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public ChatRoomService(ListingChatReader listingReader, ChatRoomRepository chatRoomRepository,
            ChatRoomParticipantRepository participantRepository, ChatMessageRepository chatMessageRepository,
            ChatMediaRepository chatMediaRepository, ChatMessageMediaRepository chatMessageMediaRepository,
            ChatRoomContextReader contextReader, ChatRoomCreator creator,
            ChatOutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.listingReader = listingReader;
        this.chatRoomRepository = chatRoomRepository;
        this.participantRepository = participantRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatMediaRepository = chatMediaRepository;
        this.chatMessageMediaRepository = chatMessageMediaRepository;
        this.contextReader = contextReader;
        this.creator = creator;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatRoomCreateResult createOrGet(Long listingId, Long buyerId) {
        ListingChatInfo listing = listingReader.findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));

        return chatRoomRepository.findByListingIdAndBuyerIdAndSellerId(
                        listingId, buyerId, listing.sellerId())
                .map(room -> {
                    rejoin(room.getId(), buyerId);
                    return ChatRoomCreateResult.existing(ChatRoomResponse.from(room));
                })
                .orElseGet(() -> create(listing, buyerId));
    }

    public Long getOrCreateChatRoom(Long listingId, Long buyerId, Long sellerId) {
        ListingChatInfo listing = listingReader.findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        if (!listing.sellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (buyerId.equals(sellerId)) {
            throw new BusinessException(ErrorCode.SELF_CHAT_NOT_ALLOWED);
        }
        return chatRoomRepository
                .findByListingIdAndBuyerIdAndSellerId(listingId, buyerId, sellerId)
                .or(() -> chatRoomRepository
                        .findFirstByBuyerIdAndSellerIdOrderByIdDesc(buyerId, sellerId))
                .map(ChatRoom::getId)
                .orElseGet(() -> createForReinspection(listingId, buyerId, sellerId));
    }

    @Transactional(readOnly = true)
    public CursorResponse<ChatRoomSummaryResponse> findRooms(Long memberId, Long cursor, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        List<ChatRoomSummaryProjection> rows = chatRoomRepository.findSummariesByMemberId(
                memberId, cursor, PageRequest.of(0, size + 1));
        boolean hasNext = rows.size() > size;
        List<ChatRoomSummaryProjection> pageRows = rows.stream().limit(size).toList();
        Map<Long, ChatRoomContext> contexts = contextReader.findAll(
                pageRows.stream().map(ChatRoomSummaryProjection::getRoomId).toList(), memberId);
        List<ChatRoomSummaryResponse> content = rows.stream()
                .limit(size)
                .map(row -> toSummary(row, memberId, contexts.get(row.getRoomId())))
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
        List<ChatMessageResponse> content = rows.stream()
                .limit(size)
                .map(message -> enrichNotification(ChatMessageResponse.from(
                        message, chatMessageMediaRepository.findMediaByMessageId(message.getMessageId()))))
                .toList();
        String nextCursor = hasNext ? content.get(content.size() - 1).roomSequence().toString() : null;
        return new CursorResponse<>(content, nextCursor, hasNext);
    }

    @Transactional
    public ChatMessageSendResult sendMessage(Long roomId, Long memberId, ChatMessageSendRequest request) {
        ChatRoomParticipant participant = participant(roomId, memberId);
        MessageType type = parseMessageType(request.type());
        String content = request.content() == null ? "" : request.content().trim();
        List<Long> mediaIds = request.mediaIds() == null ? List.of() : request.mediaIds();
        if (type == MessageType.TEXT && (content.isBlank() || !mediaIds.isEmpty())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        List<ChatMedia> media = type == MessageType.TEXT
                ? List.of()
                : validateMedia(roomId, memberId, type, mediaIds);

        return chatMessageRepository.findByChatRoomIdAndClientMessageId(roomId, request.clientMessageId())
                .map(message -> new ChatMessageSendResult(
                        ChatMessageResponse.from(
                                message, chatMessageMediaRepository.findMediaByMessageId(message.getId())),
                        false))
                .orElseGet(() -> saveNewMessage(
                        roomId, participant.getUserId(), request, type, content, media));
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

    @Transactional
    public void leaveRoom(Long roomId, Long memberId) {
        ChatRoomParticipant participant = participant(roomId, memberId);
        participant.leave();
        log.info("chat room left: roomId={}, memberId={}", roomId, memberId);
    }

    private void validateMessageCursor(Long beforeSeq, Long afterSeq, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE
                || beforeSeq != null && beforeSeq < 1
                || afterSeq != null && afterSeq < 0
                || beforeSeq != null && afterSeq != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private ChatMessageSendResult saveNewMessage(
            Long roomId,
            Long senderId,
            ChatMessageSendRequest request,
            MessageType type,
            String content,
            List<ChatMedia> media) {
        ChatRoom room = chatRoomRepository.findLockedById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
        var existing = chatMessageRepository.findByChatRoomIdAndClientMessageId(
                roomId, request.clientMessageId());
        if (existing.isPresent()) {
            return new ChatMessageSendResult(ChatMessageResponse.from(
                    existing.get(), chatMessageMediaRepository.findMediaByMessageId(existing.get().getId())), false);
        }
        long roomSequence = room.nextMessageSequence();
        ChatMessage draft = type == MessageType.TEXT
                ? ChatMessage.sendText(
                        roomId, roomSequence, senderId, request.clientMessageId(), content, LocalDateTime.now())
                : ChatMessage.sendMedia(
                        roomId, roomSequence, senderId, request.clientMessageId(), type, content, LocalDateTime.now());
        ChatMessage message = chatMessageRepository.save(draft);
        for (int index = 0; index < media.size(); index++) {
            chatMessageMediaRepository.save(ChatMessageMedia.create(
                    message.getId(), media.get(index).getId(), index));
        }
        room.recordMessage(message.getId(), roomSequence, message.getSentAt());
        log.info(
                "chat message sent: roomId={}, senderId={}, messageId={}, roomSequence={}",
                roomId,
                senderId,
                message.getId(),
                roomSequence);
        return new ChatMessageSendResult(ChatMessageResponse.from(message, media), true);
    }

    private MessageType parseMessageType(String rawType) {
        try {
            MessageType type = MessageType.valueOf(rawType);
            if (type == MessageType.SYSTEM) {
                throw new IllegalArgumentException();
            }
            return type;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private List<ChatMedia> validateMedia(
            Long roomId, Long memberId, MessageType messageType, List<Long> mediaIds) {
        if (mediaIds.size() != 1) {
            throw new BusinessException(ErrorCode.CHAT_MEDIA_INVALID);
        }
        List<ChatMedia> media = chatMediaRepository.findAllByIdInAndChatRoomIdAndUploaderIdAndUploadStatus(
                mediaIds, roomId, memberId, UploadStatus.VERIFIED);
        if (media.size() != 1 || !media.get(0).getType().name().equals(messageType.name())) {
            throw new BusinessException(ErrorCode.CHAT_MEDIA_INVALID);
        }
        return media;
    }

    private ChatRoomParticipant participant(Long roomId, Long memberId) {
        return participantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(roomId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
    }

    public record ChatMessageSendResult(ChatMessageResponse message, boolean created) {}

    private ChatMessageResponse enrichNotification(ChatMessageResponse message) {
        if (!"SYSTEM".equals(message.type()) || message.clientMessageId() == null) {
            return message;
        }
        return outboxEventRepository
                .findByEventId(message.clientMessageId())
                .map(event -> {
                    try {
                        return message.withReinspection(
                                event.getEventType(),
                                objectMapper.readValue(
                                        event.getPayload(), ReinspectionNotificationResponse.class));
                    } catch (JsonProcessingException exception) {
                        log.warn(
                                "reinspection notification payload could not be read: eventId={}",
                                message.clientMessageId());
                        return message;
                    }
                })
                .orElse(message);
    }

    private ChatRoomSummaryResponse toSummary(
            ChatRoomSummaryProjection row, Long memberId, ChatRoomContext context) {
        Long counterpartId = memberId.equals(row.getBuyerId()) ? row.getSellerId() : row.getBuyerId();
        long lastReadSeq = row.getLastReadSeq() == null ? 0L : row.getLastReadSeq();
        long counterpartLastReadSeq =
                row.getCounterpartLastReadSeq() == null ? 0L : row.getCounterpartLastReadSeq();
        long unreadCount = chatMessageRepository.countUnreadFromCounterpart(
                row.getRoomId(), memberId, lastReadSeq);
        return new ChatRoomSummaryResponse(
                row.getRoomId(), row.getListingId(), counterpartId,
                context == null ? null : context.counterpartNickname(),
                context == null ? null : context.listingTitle(),
                context == null ? null : context.listingThumbnailUrl(),
                context == null ? null : context.lastMessagePreview(),
                row.getStatus().name(),
                row.getLastMessageId(), row.getLastMessageSeq(), row.getLastMessageAt(),
                unreadCount, counterpartLastReadSeq, row.getCreatedAt());
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

    private void rejoin(Long roomId, Long memberId) {
        participantRepository.findByChatRoomIdAndUserId(roomId, memberId)
                .filter(participant -> !participantRepository
                        .existsByChatRoomIdAndUserIdAndLeftAtIsNull(roomId, memberId))
                .ifPresent(ChatRoomParticipant::rejoin);
    }

    private Long createForReinspection(Long listingId, Long buyerId, Long sellerId) {
        try {
            ChatRoom room = creator.create(listingId, buyerId, sellerId);
            log.info(
                    "chat room created for reinspection: roomId={}, listingId={}, buyerId={}, sellerId={}",
                    room.getId(),
                    listingId,
                    buyerId,
                    sellerId);
            return room.getId();
        } catch (DataIntegrityViolationException exception) {
            return chatRoomRepository
                    .findByListingIdAndBuyerIdAndSellerId(listingId, buyerId, sellerId)
                    .map(ChatRoom::getId)
                    .orElseThrow(() -> exception);
        }
    }

    private void validateCreation(ListingChatInfo listing, Long buyerId) {
        if (buyerId.equals(listing.sellerId())) {
            throw new BusinessException(ErrorCode.SELF_CHAT_NOT_ALLOWED);
        }
        if (CHAT_CREATABLE_LISTING_STATUS.equals(listing.status())) {
            return;
        }
        boolean isPurchaserPostSaleInquiry =
                POST_PURCHASE_CHAT_ALLOWED_LISTING_STATUS.contains(listing.status())
                        && buyerId.equals(listing.buyerId());
        if (!isPurchaserPostSaleInquiry) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_CREATION_NOT_ALLOWED);
        }
    }
}
