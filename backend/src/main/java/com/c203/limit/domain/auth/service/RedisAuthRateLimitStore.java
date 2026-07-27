package com.c203.limit.domain.auth.service;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "limit.auth.rate-limit.store", havingValue = "redis")
public class RedisAuthRateLimitStore implements AuthRateLimitStore {
    private static final Logger log = LoggerFactory.getLogger(RedisAuthRateLimitStore.class);
    private static final String PREFIX = "auth:rate-limit:";
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local attempts = redis.call('INCR', KEYS[1])
                    if attempts == 1 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end
                    return attempts
                    """,
                    Long.class);

    private final StringRedisTemplate redis;

    public RedisAuthRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        if (maxAttempts < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate limit policy must be positive");
        }
        Long attempts =
                redis.execute(
                        INCREMENT_SCRIPT, List.of(PREFIX + key), Long.toString(window.toMillis()));
        boolean acquired = attempts != null && attempts <= maxAttempts;
        if (!acquired) {
            log.warn("Authentication request rate limit exceeded in Redis store");
        }
        return acquired;
    }
}
