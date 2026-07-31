package com.c203.limit.domain.chat.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.c203.limit.domain.chat.entity.ChatRoomParticipant;

public interface ChatRoomParticipantRepository extends JpaRepository<ChatRoomParticipant, Long> {
    boolean existsByChatRoomIdAndUserIdAndLeftAtIsNull(Long chatRoomId, Long userId);

    Optional<ChatRoomParticipant> findByChatRoomIdAndUserIdAndLeftAtIsNull(
            Long chatRoomId, Long userId);

    Optional<ChatRoomParticipant> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);
}
