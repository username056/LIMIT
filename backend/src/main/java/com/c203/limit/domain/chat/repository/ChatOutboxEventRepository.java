package com.c203.limit.domain.chat.repository;

import com.c203.limit.domain.chat.entity.ChatOutboxEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatOutboxEventRepository extends JpaRepository<ChatOutboxEvent, Long> {
    Optional<ChatOutboxEvent> findByEventId(UUID eventId);
}
