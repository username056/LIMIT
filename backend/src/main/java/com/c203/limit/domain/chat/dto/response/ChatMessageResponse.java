package com.c203.limit.domain.chat.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

import com.c203.limit.domain.chat.entity.ChatMedia;
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
        LocalDateTime sentAt,
        List<ChatMediaResponse> media) {

    public static ChatMessageResponse from(ChatMessageProjection message, List<ChatMedia> media) {
        return new ChatMessageResponse(message.getMessageId(), message.getRoomSequence(), message.getSenderId(),
                message.getClientMessageId(), message.getType().name(), message.getContent(),
                message.getStatus().name(), message.getSentAt(),
                media.stream().map(ChatMediaResponse::from).toList());
    }

    public static ChatMessageResponse from(ChatMessage message, List<ChatMedia> media) {
        return new ChatMessageResponse(message.getId(), message.getRoomSequence(), message.getSenderId(),
                message.getClientMessageId(), message.getType().name(), message.getContent(),
                message.getStatus().name(), message.getSentAt(),
                media.stream().map(ChatMediaResponse::from).toList());
    }
}
