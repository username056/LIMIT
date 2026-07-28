package com.c203.limit.domain.chat.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.c203.limit.domain.chat.domain.MessageStatus;
import com.c203.limit.domain.chat.domain.MessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "chat_message", indexes = {
        @Index(name = "IX_CHAT_MESSAGE_ROOM_SENT", columnList = "chat_room_id,sent_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "UK_CHAT_MESSAGE_ROOM_SEQUENCE", columnNames = {"chat_room_id", "room_sequence"}),
        @UniqueConstraint(name = "UK_CHAT_MESSAGE_CLIENT", columnNames = {"chat_room_id", "client_message_id"})
})
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "room_sequence", nullable = false)
    private Long roomSequence;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "client_message_id", nullable = false)
    private UUID clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType type;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageStatus status;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected ChatMessage() {}

    public static ChatMessage sendText(
            Long chatRoomId, long roomSequence, Long senderId, UUID clientMessageId,
            String content, LocalDateTime sentAt) {
        ChatMessage message = new ChatMessage();
        message.chatRoomId = chatRoomId;
        message.roomSequence = roomSequence;
        message.senderId = senderId;
        message.clientMessageId = clientMessageId;
        message.type = MessageType.TEXT;
        message.content = content;
        message.status = MessageStatus.SENT;
        message.sentAt = sentAt;
        return message;
    }

    public static ChatMessage sendMedia(
            Long chatRoomId, long roomSequence, Long senderId, UUID clientMessageId,
            MessageType type, String content, LocalDateTime sentAt) {
        ChatMessage message = sendText(
                chatRoomId, roomSequence, senderId, clientMessageId, content, sentAt);
        message.type = type;
        return message;
    }

    public Long getId() { return id; }
    public Long getChatRoomId() { return chatRoomId; }
    public Long getRoomSequence() { return roomSequence; }
    public Long getSenderId() { return senderId; }
    public UUID getClientMessageId() { return clientMessageId; }
    public MessageType getType() { return type; }
    public String getContent() { return content; }
    public MessageStatus getStatus() { return status; }
    public LocalDateTime getSentAt() { return sentAt; }
}
