package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.auth.entity.SocialProvider;
import com.c203.limit.domain.auth.service.OAuthAttemptStore.OAuthAttempt;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** save 후 consume이 시도 정보를 정확히 한 번만 내주고(소모형), 만료/미존재 상태는 빈 값을 돌려주는지 확인한다. */
class InMemoryOAuthAttemptStoreTests {

    private final InMemoryOAuthAttemptStore store = new InMemoryOAuthAttemptStore();

    private OAuthAttempt attempt() {
        return new OAuthAttempt(SocialProvider.KAKAO, "https://limit.example.com/callback", 42L);
    }

    @Test
    void consumeReturnsTheSavedAttemptBeforeExpiry() {
        OAuthAttempt attempt = attempt();
        store.save("state-hash", attempt, Duration.ofMinutes(10));

        assertThat(store.consume("state-hash")).contains(attempt);
    }

    @Test
    void consumeIsOneTimeOnlyAndReturnsEmptyOnSecondCall() {
        store.save("state-hash", attempt(), Duration.ofMinutes(10));

        store.consume("state-hash");

        assertThat(store.consume("state-hash")).isEmpty();
    }

    @Test
    void consumeReturnsEmptyForAnUnknownState() {
        assertThat(store.consume("never-saved")).isEmpty();
    }

    @Test
    void consumeReturnsEmptyForAnAlreadyExpiredState() {
        store.save("state-hash", attempt(), Duration.ofSeconds(-1));

        assertThat(store.consume("state-hash")).isEmpty();
    }
}
