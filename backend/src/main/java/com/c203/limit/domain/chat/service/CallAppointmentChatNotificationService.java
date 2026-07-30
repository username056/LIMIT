package com.c203.limit.domain.chat.service;

import com.c203.limit.domain.call.event.CallAppointmentUpdatedNotificationEvent;
import com.c203.limit.domain.chat.dto.response.ChatMessageResponse;
import com.c203.limit.domain.chat.entity.ChatMessage;
import com.c203.limit.domain.chat.repository.ChatMessageRepository;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallAppointmentChatNotificationService {
    private static final Logger log =
            LoggerFactory.getLogger(CallAppointmentChatNotificationService.class);
    private static final DateTimeFormatter SCHEDULE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    public CallAppointmentChatNotificationService(
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationResult save(CallAppointmentUpdatedNotificationEvent event) {
        return chatMessageRepository
                .findByChatRoomIdAndClientMessageId(event.chatRoomId(), event.eventId())
                .map(message -> new NotificationResult(
                        ChatMessageResponse.from(message, java.util.List.of()), false))
                .orElseGet(() -> saveNew(event));
    }

    private NotificationResult saveNew(CallAppointmentUpdatedNotificationEvent event) {
        var room = chatRoomRepository.findLockedById(event.chatRoomId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
        long sequence = room.nextMessageSequence();
        String content = "검증 약속이 변경됐어요!"
                + "\n검증 일정: " + event.scheduledAt().format(SCHEDULE_FORMAT)
                + (event.memo() == null || event.memo().isBlank()
                        ? ""
                        : "\n메모: " + event.memo());
        ChatMessage message = chatMessageRepository.save(ChatMessage.sendSystem(
                event.chatRoomId(),
                sequence,
                event.actorId(),
                event.eventId(),
                content,
                LocalDateTime.now()));
        room.recordMessage(message.getId(), sequence, message.getSentAt());
        log.info(
                "call appointment chat notification saved: eventId={}, roomId={}, messageId={}",
                event.eventId(),
                event.chatRoomId(),
                message.getId());
        return new NotificationResult(
                ChatMessageResponse.from(message, java.util.List.of()), true);
    }

    public record NotificationResult(ChatMessageResponse message, boolean created) {}
}
