package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.auth.entity.SocialProvider;
import com.c203.limit.domain.auth.service.OAuthAttemptStore.OAuthAttempt;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * state 해시 키 구성과 "provider.base64(redirectUri).memberId" 직렬화 규약, TTL 전달, getAndDelete 기반 1회성 소모,
 * 그리고 값이 없거나 형식이 깨진 경우 빈 값으로 흡수하는지를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class RedisOAuthAttemptStoreTests {

    private static final String KEY = "auth:oauth-attempt:state-hash";
    private static final String REDIRECT_URI = "https://limit.example.com/callback";

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOperations;
    @InjectMocks RedisOAuthAttemptStore store;

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void saveWritesProviderEncodedRedirectUriAndMemberIdUnderThePrefixedKeyWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);

        store.save(
                "state-hash",
                new OAuthAttempt(SocialProvider.GOOGLE, REDIRECT_URI, 42L),
                Duration.ofMinutes(5));

        verify(valueOperations).set(eq(KEY), value.capture(), eq(Duration.ofMinutes(5)));
        assertThat(value.getValue()).isEqualTo("GOOGLE." + encode(REDIRECT_URI) + ".42");
    }

    @Test
    void saveLeavesTheMemberIdSegmentEmptyForAPlainLoginAttempt() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);

        store.save(
                "state-hash",
                new OAuthAttempt(SocialProvider.KAKAO, REDIRECT_URI, null),
                Duration.ofMinutes(5));

        verify(valueOperations).set(eq(KEY), value.capture(), eq(Duration.ofMinutes(5)));
        assertThat(value.getValue()).isEqualTo("KAKAO." + encode(REDIRECT_URI) + ".");
    }

    @Test
    void consumeReadsTheAttemptThroughGetAndDeleteSoItCanOnlyBeUsedOnce() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY))
                .thenReturn("GOOGLE." + encode(REDIRECT_URI) + ".42");

        assertThat(store.consume("state-hash"))
                .contains(new OAuthAttempt(SocialProvider.GOOGLE, REDIRECT_URI, 42L));
    }

    @Test
    void consumeRestoresABlankMemberIdSegmentAsNull() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY)).thenReturn("NAVER." + encode(REDIRECT_URI) + ".");

        assertThat(store.consume("state-hash"))
                .contains(new OAuthAttempt(SocialProvider.NAVER, REDIRECT_URI, null));
    }

    @Test
    void consumeReturnsEmptyWhenTheStateIsMissingOrAlreadyExpired() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY)).thenReturn(null);

        assertThat(store.consume("state-hash")).isEmpty();
    }

    @Test
    void consumeReturnsEmptyWhenTheStoredValueDoesNotHaveThreeFields() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY)).thenReturn("GOOGLE." + encode(REDIRECT_URI));

        assertThat(store.consume("state-hash")).isEmpty();
    }

    @Test
    void consumeReturnsEmptyWhenTheStoredProviderIsNotAKnownEnumConstant() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY))
                .thenReturn("FACEBOOK." + encode(REDIRECT_URI) + ".42");

        assertThat(store.consume("state-hash")).isEmpty();
    }

    @Test
    void consumeReturnsEmptyWhenTheStoredMemberIdIsNotNumeric() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY))
                .thenReturn("GOOGLE." + encode(REDIRECT_URI) + ".not-a-number");

        assertThat(store.consume("state-hash")).isEmpty();
    }
}
