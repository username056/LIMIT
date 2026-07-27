package com.c203.limit.domain.auth.service;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "limit.auth.password-reset.store", havingValue = "redis")
public class RedisPasswordResetTokenStore implements PasswordResetTokenStore {
    private static final Logger log = LoggerFactory.getLogger(RedisPasswordResetTokenStore.class);
    private static final String PREFIX = "auth:password-reset:";
    private final StringRedisTemplate redis;

    public RedisPasswordResetTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String tokenHash, Long memberId, Duration ttl) {
        redis.opsForValue().set(PREFIX + tokenHash, memberId.toString(), ttl);
    }

    @Override
    public Optional<Long> consume(String tokenHash) {
        String memberId = redis.opsForValue().getAndDelete(PREFIX + tokenHash);
        if (memberId == null) {
            log.warn("Password reset token could not be consumed from Redis store");
            return Optional.empty();
        }
        return Optional.of(Long.valueOf(memberId));
    }
}
