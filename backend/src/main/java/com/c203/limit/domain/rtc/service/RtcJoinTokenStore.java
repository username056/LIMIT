package com.c203.limit.domain.rtc.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RtcJoinTokenStore {
    private static final Logger log = LoggerFactory.getLogger(RtcJoinTokenStore.class);

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public Ticket issue(Long sessionId, Long memberId) {
        String token = UUID.randomUUID().toString();
        Ticket ticket = new Ticket(token, sessionId, memberId, LocalDateTime.now().plusMinutes(2));
        tickets.put(token, ticket);
        log.info("RTC signaling join ticket issued with two-minute TTL");
        return ticket;
    }

    public Ticket consume(String token) {
        Ticket ticket = token == null ? null : tickets.remove(token);
        return ticket == null || ticket.expiresAt().isBefore(LocalDateTime.now()) ? null : ticket;
    }

    public record Ticket(String token, Long sessionId, Long memberId, LocalDateTime expiresAt) {}
}
