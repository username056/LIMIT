package com.c203.limit.domain.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "limit.auth.email-verification.store",
        havingValue = "memory",
        matchIfMissing = true)
public class InMemoryEmailVerificationTokenStore implements EmailVerificationTokenStore {
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void save(String tokenHash, Long memberId, Duration ttl) {
        entries.put(tokenHash, new Entry(memberId, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<Long> consume(String tokenHash) {
        Entry entry = entries.remove(tokenHash);
        if (entry == null || !entry.expiresAt().isAfter(Instant.now())) return Optional.empty();
        return Optional.of(entry.memberId());
    }

    private record Entry(Long memberId, Instant expiresAt) {}
}
