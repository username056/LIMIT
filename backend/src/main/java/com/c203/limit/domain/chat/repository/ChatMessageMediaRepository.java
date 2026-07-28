package com.c203.limit.domain.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.c203.limit.domain.chat.entity.ChatMedia;
import com.c203.limit.domain.chat.entity.ChatMessageMedia;
import com.c203.limit.domain.chat.entity.ChatMessageMediaId;

public interface ChatMessageMediaRepository
        extends JpaRepository<ChatMessageMedia, ChatMessageMediaId> {
    List<ChatMessageMedia> findAllByIdChatMessageId(Long messageId);

    @Query("""
            SELECT media
              FROM ChatMessageMedia link, ChatMedia media
             WHERE link.id.chatMessageId = :messageId
               AND media.id = link.id.chatMediaId
             ORDER BY link.displayOrder
            """)
    List<ChatMedia> findMediaByMessageId(@Param("messageId") Long messageId);
}
