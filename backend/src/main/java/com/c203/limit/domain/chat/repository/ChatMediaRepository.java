package com.c203.limit.domain.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.c203.limit.domain.chat.domain.UploadStatus;
import com.c203.limit.domain.chat.entity.ChatMedia;

public interface ChatMediaRepository extends JpaRepository<ChatMedia, Long> {
    List<ChatMedia> findAllByIdInAndChatRoomIdAndUploaderIdAndUploadStatus(
            List<Long> ids, Long chatRoomId, Long uploaderId, UploadStatus uploadStatus);
}
