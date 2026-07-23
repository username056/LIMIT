package com.c203.limit.domain.auth.service;

import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "limit.auth.email-verification.store", havingValue = "redis")
public class RedisEmailVerificationTokenStore implements EmailVerificationTokenStore {
    private static final String PREFIX = "auth:email-verification:";
    private final StringRedisTemplate redis;

    public RedisEmailVerificationTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String tokenHash, Long memberId, Duration ttl) {
        redis.opsForValue().set(PREFIX + tokenHash, memberId.toString(), ttl);
    }

    @Override
    public Optional<Long> consume(String tokenHash) {
        String memberId = redis.opsForValue().getAndDelete(PREFIX + tokenHash);
        return memberId == null ? Optional.empty() : Optional.of(Long.valueOf(memberId));
    }
}
