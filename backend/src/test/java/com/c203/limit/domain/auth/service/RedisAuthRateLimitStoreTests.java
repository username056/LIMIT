package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * in-memory 구현과 달리 Redis 구현은 INCR + PEXPIRE Lua 스크립트 한 번으로 카운트를 올린다. 여기서는 스크립트에 넘기는 키/윈도우(ms) 인자, 한계값
 * 경계 판정, 스크립트가 null을 돌려준 실패 상황, 잘못된 정책 인자 검증을 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class RedisAuthRateLimitStoreTests {

    @Mock StringRedisTemplate redis;
    @InjectMocks RedisAuthRateLimitStore store;

    @Captor ArgumentCaptor<List<String>> keys;
    @Captor ArgumentCaptor<String> windowMillis;

    @Test
    void tryAcquireRunsTheScriptOnThePrefixedKeyWithTheWindowInMilliseconds() {
        when(redis.<Long>execute(any(), anyList(), anyString())).thenReturn(1L);

        assertThat(store.tryAcquire("login:user@example.com", 5, Duration.ofMinutes(10))).isTrue();

        verify(redis)
                .execute(
                        org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                        keys.capture(),
                        windowMillis.capture());
        assertThat(keys.getValue()).containsExactly("auth:rate-limit:login:user@example.com");
        assertThat(windowMillis.getValue()).isEqualTo("600000");
    }

    @Test
    void tryAcquireStillAllowsTheAttemptThatExactlyReachesTheLimit() {
        when(redis.<Long>execute(any(), anyList(), anyString())).thenReturn(3L);

        assertThat(store.tryAcquire("login:key", 3, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void tryAcquireRejectsTheAttemptThatExceedsTheLimit() {
        when(redis.<Long>execute(any(), anyList(), anyString())).thenReturn(4L);

        assertThat(store.tryAcquire("login:key", 3, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void tryAcquireRejectsWhenTheScriptReturnsNoCounter() {
        when(redis.<Long>execute(any(), anyList(), anyString())).thenReturn(null);

        assertThat(store.tryAcquire("login:key", 3, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void tryAcquireRejectsAPolicyWithoutAnyAllowedAttempt() {
        assertThatThrownBy(() -> store.tryAcquire("login:key", 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(redis);
    }

    @Test
    void tryAcquireRejectsAMissingWindow() {
        assertThatThrownBy(() -> store.tryAcquire("login:key", 5, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(redis);
    }

    @Test
    void tryAcquireRejectsAZeroWindow() {
        assertThatThrownBy(() -> store.tryAcquire("login:key", 5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(redis);
    }

    @Test
    void tryAcquireRejectsANegativeWindow() {
        assertThatThrownBy(() -> store.tryAcquire("login:key", 5, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(redis);
    }
}
