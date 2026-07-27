package com.c203.limit.domain.auth.service;

import java.time.Duration;

public interface AuthRateLimitStore {
    boolean tryAcquire(String key, int maxAttempts, Duration window);
}
