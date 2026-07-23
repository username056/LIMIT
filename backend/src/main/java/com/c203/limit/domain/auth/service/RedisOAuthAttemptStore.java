package com.c203.limit.domain.auth.service;

import com.c203.limit.domain.auth.entity.SocialProvider;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "limit.auth.oauth-attempt.store", havingValue = "redis")
public class RedisOAuthAttemptStore implements OAuthAttemptStore {
    private static final String PREFIX = "auth:oauth-attempt:";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final StringRedisTemplate redis;

    public RedisOAuthAttemptStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String stateHash, OAuthAttempt attempt, Duration ttl) {
        redis.opsForValue()
                .set(
                        PREFIX + stateHash,
                        attempt.provider().name()
                                + "."
                                + encode(attempt.redirectUri())
                                + "."
                                + (attempt.memberId() == null ? "" : attempt.memberId()),
                        ttl);
    }

    @Override
    public Optional<OAuthAttempt> consume(String stateHash) {
        String value = redis.opsForValue().getAndDelete(PREFIX + stateHash);
        if (value == null) return Optional.empty();
        String[] fields = value.split("\\.", -1);
        if (fields.length != 3) return Optional.empty();
        try {
            return Optional.of(
                    new OAuthAttempt(
                            SocialProvider.valueOf(fields[0]),
                            decode(fields[1]),
                            fields[2].isBlank() ? null : Long.valueOf(fields[2])));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String encode(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }
}
