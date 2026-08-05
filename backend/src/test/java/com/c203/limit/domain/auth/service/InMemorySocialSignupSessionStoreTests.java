package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.auth.entity.SocialProvider;
import com.c203.limit.domain.auth.service.SocialSignupSessionStore.SocialSignupSession;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** save 후 consume이 세션을 정확히 한 번만 내주고(소모형), 만료/미존재 토큰은 빈 값을 돌려주는지 확인한다. */
class InMemorySocialSignupSessionStoreTests {

    private final InMemorySocialSignupSessionStore store = new InMemorySocialSignupSessionStore();

    private SocialSignupSession session() {
        return new SocialSignupSession(SocialProvider.GOOGLE, "provider-user-1", "user@example.com", "닉네임");
    }

    @Test
    void consumeReturnsTheSavedSessionBeforeExpiry() {
        SocialSignupSession session = session();
        store.save("token-hash", session, Duration.ofMinutes(10));

        assertThat(store.consume("token-hash")).contains(session);
    }

    @Test
    void consumeIsOneTimeOnlyAndReturnsEmptyOnSecondCall() {
        store.save("token-hash", session(), Duration.ofMinutes(10));

        store.consume("token-hash");

        assertThat(store.consume("token-hash")).isEmpty();
    }

    @Test
    void consumeReturnsEmptyForAnUnknownToken() {
        assertThat(store.consume("never-saved")).isEmpty();
    }

    @Test
    void consumeReturnsEmptyForAnAlreadyExpiredToken() {
        store.save("token-hash", session(), Duration.ofSeconds(-1));

        assertThat(store.consume("token-hash")).isEmpty();
    }
}
