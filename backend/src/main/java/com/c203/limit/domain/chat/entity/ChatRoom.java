package com.c203.limit.domain.chat.entity;

import java.time.LocalDateTime;

import com.c203.limit.domain.chat.domain.ChatRoomStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "chat_room", uniqueConstraints = @UniqueConstraint(
        name = "UK_CHAT_ROOM_LISTING_USERS",
        columnNames = {"listing_id", "buyer_id", "seller_id"}))
public class ChatRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomStatus status;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_seq", nullable = false)
    private long lastMessageSeq;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    protected ChatRoom() {}

    public static ChatRoom create(Long listingId, Long buyerId, Long sellerId) {
        ChatRoom room = new ChatRoom();
        room.listingId = listingId;
        room.buyerId = buyerId;
        room.sellerId = sellerId;
        room.status = ChatRoomStatus.ACTIVE;
        room.lastMessageSeq = 0L;
        room.createdAt = LocalDateTime.now();
        room.updatedAt = room.createdAt;
        return room;
    }

    public Long getId() { return id; }
    public Long getListingId() { return listingId; }
    public Long getBuyerId() { return buyerId; }
    public Long getSellerId() { return sellerId; }
    public ChatRoomStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
