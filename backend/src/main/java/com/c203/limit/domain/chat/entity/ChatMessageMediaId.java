package com.c203.limit.domain.chat.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record ChatMessageMediaId(
        @Column(name = "chat_message_id") Long chatMessageId,
        @Column(name = "chat_media_id") Long chatMediaId) implements Serializable {

    public ChatMessageMediaId() {
        this(null, null);
    }
}
