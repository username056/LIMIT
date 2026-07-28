package com.c203.limit.domain.chat.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatAckResponse(
        UUID clientMessageId,
        Long messageId,
        Long roomSequence,
        String status,
        LocalDateTime sentAt) {

    public static ChatAckResponse sent(ChatMessageResponse message) {
        return new ChatAckResponse(
                message.clientMessageId(),
                message.messageId(),
                message.roomSequence(),
                message.status(),
                message.sentAt());
    }
}
