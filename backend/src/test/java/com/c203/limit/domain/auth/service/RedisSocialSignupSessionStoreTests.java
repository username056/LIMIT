package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.auth.entity.SocialProvider;
import com.c203.limit.domain.auth.service.SocialSignupSessionStore.SocialSignupSession;
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
 * Redis 구현은 in-memory 구현과 달리 "키 접두사 + '.'로 이어붙인 Base64 문자열"이라는 직렬화 규약에 의존한다. 키 구성, TTL 전달, 한 번만 꺼내가는
 * getAndDelete 위임, 그리고 값이 없거나 형식이 깨졌을 때 예외 대신 빈 값을 돌려주는지를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class RedisSocialSignupSessionStoreTests {

    private static final String KEY = "auth:social-signup:token-hash";

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOperations;
    @InjectMocks RedisSocialSignupSessionStore store;

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void saveWritesBase64EncodedFieldsUnderThePrefixedKeyWithTheGivenTtl() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);

        store.save(
                "token-hash",
                new SocialSignupSession(
                        SocialProvider.GOOGLE, "g-1", "user@example.com", "runner"),
                Duration.ofMinutes(10));

        verify(valueOperations).set(eq(KEY), value.capture(), eq(Duration.ofMinutes(10)));
        assertThat(value.getValue())
                .isEqualTo(
                        "GOOGLE."
                                + encode("g-1")
                                + "."
                                + encode("user@example.com")
                                + "."
                                + encode("runner"));
    }

    @Test
    void saveEncodesNullOptionalFieldsAsEmptySegmentsSoTheValueKeepsFourFields() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);

        store.save(
                "token-hash",
                new SocialSignupSession(SocialProvider.KAKAO, "k-1", null, null),
                Duration.ofMinutes(5));

        verify(valueOperations).set(eq(KEY), value.capture(), eq(Duration.ofMinutes(5)));
        assertThat(value.getValue().split("\\.", -1)).hasSize(4);
        assertThat(value.getValue()).isEqualTo("KAKAO." + encode("k-1") + "..");
    }

    @Test
    void consumeReadsTheSessionThroughGetAndDeleteSoItCanOnlyBeUsedOnce() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY))
                .thenReturn(
                        "NAVER."
                                + encode("n-1")
                                + "."
                                + encode("user@example.com")
                                + "."
                                + encode("러너"));

        assertThat(store.consume("token-hash"))
                .contains(
                        new SocialSignupSession(
                                SocialProvider.NAVER, "n-1", "user@example.com", "러너"));
    }

    @Test
    void consumeRestoresEmptySegmentsAsEmptyStrings() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY)).thenReturn("KAKAO." + encode("k-1") + "..");

        assertThat(store.consume("token-hash"))
                .contains(new SocialSignupSession(SocialProvider.KAKAO, "k-1", "", ""));
    }

    @Test
    void consumeReturnsEmptyWhenTheKeyIsMissingOrAlreadyExpired() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY)).thenReturn(null);

        assertThat(store.consume("token-hash")).isEmpty();
    }

    @Test
    void consumeReturnsEmptyWhenTheStoredValueDoesNotHaveFourFields() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY)).thenReturn("GOOGLE." + encode("g-1"));

        assertThat(store.consume("token-hash")).isEmpty();
    }

    @Test
    void consumeReturnsEmptyWhenTheStoredProviderIsNotAKnownEnumConstant() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY))
                .thenReturn("FACEBOOK." + encode("f-1") + "." + encode("u@e.com") + ".");

        assertThat(store.consume("token-hash")).isEmpty();
    }

    @Test
    void consumeReturnsEmptyWhenAFieldIsNotValidBase64() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(KEY)).thenReturn("GOOGLE.***.***.***");

        assertThat(store.consume("token-hash")).isEmpty();
    }
}
