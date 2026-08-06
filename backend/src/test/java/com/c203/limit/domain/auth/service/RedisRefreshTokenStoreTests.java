package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * in-memory 구현과 달리 Redis 구현은 토큰 키(auth:refresh:*)와 subject 인덱스 집합(auth:refresh-subject:*) 두 개를 함께
 * 관리한다. 키 구성/TTL, accountType 소문자 정규화, 조회 실패 시 false 반환, 삭제 위임 범위를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenStoreTests {

    private static final String SUBJECT_KEY = "auth:refresh-subject:member:42";

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock SetOperations<String, String> setOperations;
    @InjectMocks RedisRefreshTokenStore store;

    @Captor ArgumentCaptor<Collection<String>> deletedKeys;

    @Test
    void saveStoresTheTokenWithTtlAndRegistersItInTheExpiringSubjectIndex() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(redis.opsForSet()).thenReturn(setOperations);

        store.save("token-1", 42L, "MEMBER", Duration.ofDays(14));

        verify(valueOperations).set("auth:refresh:token-1", "MEMBER:42", Duration.ofDays(14));
        verify(setOperations).add(SUBJECT_KEY, "token-1");
        verify(redis).expire(SUBJECT_KEY, Duration.ofDays(14));
    }

    @Test
    void saveLowerCasesOnlyTheAccountTypeSegmentOfTheSubjectIndexKey() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(redis.opsForSet()).thenReturn(setOperations);

        store.save("token-1", 7L, "ADMIN", Duration.ofDays(1));

        // 값에는 원래 표기가, 인덱스 키에는 소문자 표기가 쓰인다.
        verify(valueOperations).set("auth:refresh:token-1", "ADMIN:7", Duration.ofDays(1));
        verify(setOperations).add("auth:refresh-subject:admin:7", "token-1");
    }

    @Test
    void isValidReturnsTrueWhenTheStoredOwnerMatches() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:refresh:token-1")).thenReturn("MEMBER:42");

        assertThat(store.isValid("token-1", 42L, "MEMBER")).isTrue();
    }

    @Test
    void isValidReturnsFalseWhenTheTokenKeyIsMissing() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:refresh:unknown")).thenReturn(null);

        assertThat(store.isValid("unknown", 42L, "MEMBER")).isFalse();
    }

    @Test
    void isValidReturnsFalseWhenTheStoredSubjectIdDiffers() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:refresh:token-1")).thenReturn("MEMBER:99");

        assertThat(store.isValid("token-1", 42L, "MEMBER")).isFalse();
    }

    @Test
    void isValidReturnsFalseWhenTheStoredAccountTypeDiffers() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:refresh:token-1")).thenReturn("ADMIN:42");

        assertThat(store.isValid("token-1", 42L, "MEMBER")).isFalse();
    }

    @Test
    void revokeDeletesOnlyTheTokenKey() {
        store.revoke("token-1");

        verify(redis).delete("auth:refresh:token-1");
    }

    @Test
    void revokeAllDeletesEveryIndexedTokenKeyAndThenTheSubjectIndexItself() {
        when(redis.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(SUBJECT_KEY))
                .thenReturn(new LinkedHashSet<>(Set.of("token-1", "token-2")));

        store.revokeAll(42L, "MEMBER");

        verify(redis).delete(deletedKeys.capture());
        assertThat(deletedKeys.getValue())
                .containsExactlyInAnyOrder("auth:refresh:token-1", "auth:refresh:token-2");
        verify(redis).delete(SUBJECT_KEY);
    }

    @Test
    void revokeAllOnlyDropsTheSubjectIndexWhenNoTokenIsRegistered() {
        when(redis.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(SUBJECT_KEY)).thenReturn(Set.of());

        store.revokeAll(42L, "MEMBER");

        verify(redis, never()).delete(anyCollection());
        verify(redis).delete(SUBJECT_KEY);
    }

    @Test
    void revokeAllToleratesANullMembersReplyFromRedis() {
        when(redis.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(SUBJECT_KEY)).thenReturn(null);

        store.revokeAll(42L, "MEMBER");

        verify(redis, never()).delete(anyCollection());
        verify(redis).delete(SUBJECT_KEY);
    }
}
