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
        name = "limit.auth.social-signup.store",
        havingValue = "memory",
        matchIfMissing = true)
public class InMemorySocialSignupSessionStore implements SocialSignupSessionStore {
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void save(String tokenHash, SocialSignupSession session, Duration ttl) {
        entries.put(tokenHash, new Entry(session, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<SocialSignupSession> consume(String tokenHash) {
        Entry entry = entries.remove(tokenHash);
        if (entry == null || !entry.expiresAt().isAfter(Instant.now())) return Optional.empty();
        return Optional.of(entry.session());
    }

    private record Entry(SocialSignupSession session, Instant expiresAt) {}
}
