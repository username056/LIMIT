package com.c203.limit.domain.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "limit.auth.password-reset.store",
        havingValue = "memory",
        matchIfMissing = true)
public class InMemoryPasswordResetTokenStore implements PasswordResetTokenStore {
    private static final Logger log =
            LoggerFactory.getLogger(InMemoryPasswordResetTokenStore.class);
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void save(String tokenHash, Long memberId, Duration ttl) {
        entries.put(tokenHash, new Entry(memberId, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<Long> consume(String tokenHash) {
        Entry entry = entries.remove(tokenHash);
        if (entry == null || !entry.expiresAt().isAfter(Instant.now())) {
            log.warn("Password reset token could not be consumed from memory store");
            return Optional.empty();
        }
        return Optional.of(entry.memberId());
    }

    private record Entry(Long memberId, Instant expiresAt) {}
}
