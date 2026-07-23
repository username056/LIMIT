package com.c203.limit.domain.auth.service;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "limit.security.refresh-store", havingValue = "redis")
public class RedisRefreshTokenStore implements RefreshTokenStore {
    private static final String TOKEN_PREFIX = "auth:refresh:";
    private static final String SUBJECT_PREFIX = "auth:refresh-subject:";
    private final StringRedisTemplate redis;

    public RedisRefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void save(String tokenId, Long subjectId, String accountType, Duration ttl) {
        redis.opsForValue().set(TOKEN_PREFIX + tokenId, accountType + ":" + subjectId, ttl);
        String subjectKey = subjectKey(subjectId, accountType);
        redis.opsForSet().add(subjectKey, tokenId);
        redis.expire(subjectKey, ttl);
    }

    public boolean isValid(String tokenId, Long subjectId, String accountType) {
        return (accountType + ":" + subjectId)
                .equals(redis.opsForValue().get(TOKEN_PREFIX + tokenId));
    }

    public void revoke(String tokenId) {
        redis.delete(TOKEN_PREFIX + tokenId);
    }

    public void revokeAll(Long subjectId, String accountType) {
        String subjectKey = subjectKey(subjectId, accountType);
        Set<String> tokenIds = redis.opsForSet().members(subjectKey);
        if (tokenIds != null && !tokenIds.isEmpty())
            redis.delete(tokenIds.stream().map(id -> TOKEN_PREFIX + id).toList());
        redis.delete(subjectKey);
    }

    private String subjectKey(Long subjectId, String accountType) {
        return SUBJECT_PREFIX + accountType.toLowerCase(java.util.Locale.ROOT) + ":" + subjectId;
    }
}
