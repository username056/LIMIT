package com.c203.limit.domain.chat.service;

import com.c203.limit.domain.chat.dto.response.ChatMessageResponse;
import com.c203.limit.domain.chat.dto.response.ReinspectionNotificationResponse;
import com.c203.limit.domain.chat.entity.ChatOutboxEvent;
import com.c203.limit.domain.chat.entity.ChatMessage;
import com.c203.limit.domain.chat.entity.ChatRoom;
import com.c203.limit.domain.chat.entity.ReinspectionRequestMessage;
import com.c203.limit.domain.chat.repository.ChatOutboxEventRepository;
import com.c203.limit.domain.chat.repository.ChatMessageRepository;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.chat.repository.ReinspectionRequestMessageRepository;
import com.c203.limit.domain.inspection.event.ReinspectionNotificationEvent;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReinspectionChatNotificationService {
    private static final Logger log =
            LoggerFactory.getLogger(ReinspectionChatNotificationService.class);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ReinspectionRequestMessageRepository requestMessageRepository;
    private final ChatOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public ReinspectionChatNotificationService(
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            ReinspectionRequestMessageRepository requestMessageRepository,
            ChatOutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.requestMessageRepository = requestMessageRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationResult save(ReinspectionNotificationEvent event) {
        return chatMessageRepository
                .findByChatRoomIdAndClientMessageId(event.chatRoomId(), event.eventId())
                .map(message -> new NotificationResult(
                        ChatMessageResponse.from(message, java.util.List.of()),
                        null,
                        false))
                .orElseGet(() -> saveNew(event));
    }

    private NotificationResult saveNew(ReinspectionNotificationEvent event) {
        ChatRoom room = chatRoomRepository.findLockedById(event.chatRoomId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REINSPECTION_CHAT_ROOM_NOT_FOUND));
        long sequence = room.nextMessageSequence();
        ChatMessage message = chatMessageRepository.save(ChatMessage.sendSystem(
                event.chatRoomId(),
                sequence,
                event.actorId(),
                event.eventId(),
                content(event),
                LocalDateTime.now()));
        requestMessageRepository.save(ReinspectionRequestMessage.link(
                event.reinspectionRequestId(),
                event.type().name(),
                message.getId(),
                LocalDateTime.now()));
        ChatOutboxEvent outboxEvent = outboxEventRepository.save(ChatOutboxEvent.pending(
                event.eventId(),
                "REINSPECTION_REQUEST",
                event.reinspectionRequestId(),
                "REINSPECTION_" + event.type().name(),
                serializePayload(event),
                LocalDateTime.now()));
        room.recordMessage(message.getId(), sequence, message.getSentAt());
        log.info(
                "reinspection chat notification saved: eventId={}, type={}, roomId={}, recipientId={}",
                event.eventId(), event.type(), event.chatRoomId(), event.recipientId());
        return new NotificationResult(
                ChatMessageResponse.from(message, java.util.List.of()),
                outboxEvent.getId(),
                true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(Long outboxEventId) {
        if (outboxEventId == null) {
            return;
        }
        outboxEventRepository.findById(outboxEventId)
                .ifPresent(event -> event.markPublished(LocalDateTime.now()));
    }

    private String content(ReinspectionNotificationEvent event) {
        if (event.type() == ReinspectionNotificationEvent.Type.COMPLETED) {
            return "재검수가 완료되었습니다.";
        }
        String itemLines = event.items().stream()
                .map(item -> "- " + item.name() + ": " + item.requestContent())
                .collect(Collectors.joining("\n"));
        return "재검수 요청이 " + event.pendingRequestCount() + "건 들어왔어요!"
                + "\n펼쳐보기"
                + "\n사유: " + event.reason()
                + "\n" + itemLines
                + "\n바로 재촬영하기";
    }

    private String serializePayload(ReinspectionNotificationEvent event) {
        try {
            return objectMapper.writeValueAsString(
                    ReinspectionNotificationResponse.from(event));
        } catch (JsonProcessingException exception) {
            log.error(
                    "reinspection outbox payload serialization failed; fallback payload used: eventId={}, type={}, requestId={}",
                    event.eventId(),
                    event.type(),
                    event.reinspectionRequestId(),
                    exception);
            return JsonNodeFactory.instance
                    .objectNode()
                    .put("eventId", event.eventId().toString())
                    .put("type", event.type().name())
                    .put("reinspectionRequestId", event.reinspectionRequestId())
                    .put("requestKey", event.requestKey())
                    .put("chatRoomId", event.chatRoomId())
                    .put("recipientId", event.recipientId())
                    .put("serializationFallback", true)
                    .toString();
        }
    }

    public record NotificationResult(
            ChatMessageResponse message, Long outboxEventId, boolean created) {}
}
