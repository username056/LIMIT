package com.c203.limit.domain.chat.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.c203.limit.domain.chat.entity.ChatMessage;
import com.c203.limit.domain.chat.repository.ChatMessageProjection;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ChatMessageResponse", description = "채팅 메시지 응답")
public record ChatMessageResponse(
        Long messageId,
        Long roomSequence,
        Long senderId,
        UUID clientMessageId,
        String type,
        String content,
        String status,
        LocalDateTime sentAt) {

    public static ChatMessageResponse from(ChatMessageProjection message) {
        return new ChatMessageResponse(message.getMessageId(), message.getRoomSequence(), message.getSenderId(),
                message.getClientMessageId(), message.getType().name(), message.getContent(),
                message.getStatus().name(), message.getSentAt());
    }

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(message.getId(), message.getRoomSequence(), message.getSenderId(),
                message.getClientMessageId(), message.getType().name(), message.getContent(),
                message.getStatus().name(), message.getSentAt());
    }
}
