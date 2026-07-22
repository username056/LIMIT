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
        name = "limit.auth.oauth-attempt.store",
        havingValue = "memory",
        matchIfMissing = true)
public class InMemoryOAuthAttemptStore implements OAuthAttemptStore {
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void save(String stateHash, OAuthAttempt attempt, Duration ttl) {
        entries.put(stateHash, new Entry(attempt, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<OAuthAttempt> consume(String stateHash) {
        Entry entry = entries.remove(stateHash);
        if (entry == null || !entry.expiresAt().isAfter(Instant.now())) return Optional.empty();
        return Optional.of(entry.attempt());
    }

    private record Entry(OAuthAttempt attempt, Instant expiresAt) {}
}
