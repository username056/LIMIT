package com.c203.limit.domain.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "limit.security.refresh-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryRefreshTokenStore implements RefreshTokenStore {
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    public void save(String tokenId, Long subjectId, Duration ttl) {
        entries.put(tokenId, new Entry(subjectId, Instant.now().plus(ttl)));
    }
    public boolean isValid(String tokenId, Long subjectId) {
        Entry entry = entries.get(tokenId);
        return entry != null && entry.subjectId().equals(subjectId) && entry.expiresAt().isAfter(Instant.now());
    }
    public void revoke(String tokenId) { entries.remove(tokenId); }
    public void revokeAll(Long subjectId) { entries.entrySet().removeIf(e -> e.getValue().subjectId().equals(subjectId)); }
    private record Entry(Long subjectId, Instant expiresAt) {}
}
