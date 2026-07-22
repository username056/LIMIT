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
@ConditionalOnProperty(name = "limit.auth.social-signup.store", havingValue = "redis")
public class RedisSocialSignupSessionStore implements SocialSignupSessionStore {
    private static final String PREFIX = "auth:social-signup:";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final StringRedisTemplate redis;

    public RedisSocialSignupSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String tokenHash, SocialSignupSession session, Duration ttl) {
        redis.opsForValue()
                .set(
                        PREFIX + tokenHash,
                        String.join(
                                ".",
                                session.provider().name(),
                                encode(session.providerUserId()),
                                encode(session.email()),
                                encode(session.suggestedNickname())),
                        ttl);
    }

    @Override
    public Optional<SocialSignupSession> consume(String tokenHash) {
        String value = redis.opsForValue().getAndDelete(PREFIX + tokenHash);
        if (value == null) return Optional.empty();
        String[] fields = value.split("\\.", -1);
        if (fields.length != 4) return Optional.empty();
        try {
            return Optional.of(
                    new SocialSignupSession(
                            SocialProvider.valueOf(fields[0]),
                            decode(fields[1]),
                            decode(fields[2]),
                            decode(fields[3])));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String encode(String value) {
        String safe = value == null ? "" : value;
        return ENCODER.encodeToString(safe.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }
}
