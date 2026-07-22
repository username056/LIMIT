package com.c203.limit.domain.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.c203.limit.domain.chat.entity.ChatRoomParticipant;

public interface ChatRoomParticipantRepository extends JpaRepository<ChatRoomParticipant, Long> {
}
