package com.c203.limit.domain.chat.repository;

import com.c203.limit.domain.chat.entity.ChatOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatOutboxEventRepository extends JpaRepository<ChatOutboxEvent, Long> {}
