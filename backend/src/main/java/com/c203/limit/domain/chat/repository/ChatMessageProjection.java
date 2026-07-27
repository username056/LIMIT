package com.c203.limit.domain.chat.repository;

import java.time.LocalDateTime;
import java.util.UUID;

import com.c203.limit.domain.chat.domain.MessageStatus;
import com.c203.limit.domain.chat.domain.MessageType;

public interface ChatMessageProjection {
    Long getMessageId();
    Long getRoomSequence();
    Long getSenderId();
    UUID getClientMessageId();
    MessageType getType();
    String getContent();
    MessageStatus getStatus();
    LocalDateTime getSentAt();
}
