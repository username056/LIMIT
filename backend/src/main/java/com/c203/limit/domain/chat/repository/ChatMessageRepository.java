package com.c203.limit.domain.chat.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.c203.limit.domain.chat.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @Query("""
            SELECT message.id AS messageId, message.roomSequence AS roomSequence,
                   message.senderId AS senderId, message.clientMessageId AS clientMessageId,
                   message.type AS type, message.content AS content,
                   message.status AS status, message.sentAt AS sentAt
              FROM ChatMessage message
             WHERE message.chatRoomId = :roomId
               AND message.deletedAt IS NULL
               AND (:beforeSeq IS NULL OR message.roomSequence < :beforeSeq)
             ORDER BY message.roomSequence DESC
            """)
    List<ChatMessageProjection> findBeforeSequence(@Param("roomId") Long roomId,
            @Param("beforeSeq") Long beforeSeq, Pageable pageable);

    @Query("""
            SELECT message.id AS messageId, message.roomSequence AS roomSequence,
                   message.senderId AS senderId, message.clientMessageId AS clientMessageId,
                   message.type AS type, message.content AS content,
                   message.status AS status, message.sentAt AS sentAt
              FROM ChatMessage message
             WHERE message.chatRoomId = :roomId
               AND message.deletedAt IS NULL
               AND message.roomSequence > :afterSeq
             ORDER BY message.roomSequence ASC
            """)
    List<ChatMessageProjection> findAfterSequence(@Param("roomId") Long roomId,
            @Param("afterSeq") Long afterSeq, Pageable pageable);
}
