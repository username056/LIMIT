package com.c203.limit.domain.chat.entity;

import java.time.LocalDateTime;

import com.c203.limit.domain.chat.domain.ParticipantRole;
import com.c203.limit.global.common.BaseTimeEntity;
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
@Table(name = "chat_room_participant", uniqueConstraints = {
        @UniqueConstraint(name = "UK_CHAT_ROOM_PARTICIPANT_USER", columnNames = {"chat_room_id", "user_id"}),
        @UniqueConstraint(name = "UK_CHAT_ROOM_PARTICIPANT_ROLE", columnNames = {"chat_room_id", "participant_role"})
})
public class ChatRoomParticipant extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_role", nullable = false, length = 20)
    private ParticipantRole role;

    @Column(name = "last_read_seq", nullable = false)
    private long lastReadSeq;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    protected ChatRoomParticipant() {}

    public static ChatRoomParticipant create(Long chatRoomId, Long userId, ParticipantRole role) {
        ChatRoomParticipant participant = new ChatRoomParticipant();
        participant.chatRoomId = chatRoomId;
        participant.userId = userId;
        participant.role = role;
        participant.lastReadSeq = 0L;
        participant.joinedAt = LocalDateTime.now();
        return participant;
    }

    public void readUpTo(long roomSequence) {
        if (roomSequence > lastReadSeq) {
            lastReadSeq = roomSequence;
        }
    }

    public void leave() {
        leftAt = LocalDateTime.now();
    }

    public void rejoin() {
        leftAt = null;
        joinedAt = LocalDateTime.now();
    }

    public Long getChatRoomId() { return chatRoomId; }
    public Long getUserId() { return userId; }
    public long getLastReadSeq() { return lastReadSeq; }
    public LocalDateTime getLeftAt() { return leftAt; }
}
