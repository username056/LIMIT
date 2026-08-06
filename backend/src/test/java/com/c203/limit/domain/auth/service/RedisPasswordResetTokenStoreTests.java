package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** 키 접두사와 TTL 전달, getAndDelete 기반 1회성 소모, 토큰이 없거나 값이 깨졌을 때의 동작을 확인한다. */
@ExtendWith(MockitoExtension.class)
class RedisPasswordResetTokenStoreTests {

    private static final String KEY = "auth:password-reset:token-hash";

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOperations;
    @InjectMocks RedisPasswordResetTokenStore store;

    @Test
    void saveWritesTheMemberIdUnderThePrefixedKeyWithTheGivenTtl() {
        when(redis.opsForValue()).thenReturn(valueOperations);

        store.save("token-hash", 42L, Duration.ofMinutes(30));

        verify(valueOperations).set(KEY, "42", Duration.ofMinutes(30));
    }

    @Test
    void consumeReadsTheMemberIdThroughGetAndDeleteSoTheTokenCannotBeReused() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY)).thenReturn("42");

        assertThat(store.consume("token-hash")).contains(42L);
    }

    @Test
    void consumeReturnsEmptyWhenTheTokenIsMissingOrAlreadyExpired() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY)).thenReturn(null);

        assertThat(store.consume("token-hash")).isEmpty();
    }

    @Test
    void consumeFailsFastWhenTheStoredValueIsNotANumericMemberId() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY)).thenReturn("corrupted");

        assertThatThrownBy(() -> store.consume("token-hash"))
                .isInstanceOf(NumberFormatException.class);
    }
}
