package com.c203.limit.domain.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "limit.auth.rate-limit.store",
        havingValue = "memory",
        matchIfMissing = true)
public class InMemoryAuthRateLimitStore implements AuthRateLimitStore {
    private static final Logger log = LoggerFactory.getLogger(InMemoryAuthRateLimitStore.class);
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean tryAcquire(String key, int maxAttempts, Duration window) {
        validate(maxAttempts, window);
        Instant now = Instant.now();
        Window current = windows.get(key);
        if (current == null || !current.expiresAt().isAfter(now)) {
            windows.put(key, new Window(1, now.plus(window)));
            return true;
        }
        if (current.attempts() >= maxAttempts) {
            log.warn("Authentication request rate limit exceeded in memory store");
            return false;
        }
        windows.put(key, new Window(current.attempts() + 1, current.expiresAt()));
        return true;
    }

    private void validate(int maxAttempts, Duration window) {
        if (maxAttempts < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate limit policy must be positive");
        }
    }

    private record Window(int attempts, Instant expiresAt) {}
}
