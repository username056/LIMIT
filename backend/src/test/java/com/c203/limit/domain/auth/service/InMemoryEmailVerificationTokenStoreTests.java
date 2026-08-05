package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** save 후 consume이 정확히 한 번만 값을 내주고(소모형), 만료/미존재 토큰은 빈 값을 돌려주는지 확인한다. */
class InMemoryEmailVerificationTokenStoreTests {

    private final InMemoryEmailVerificationTokenStore store = new InMemoryEmailVerificationTokenStore();

    @Test
    void consumeReturnsTheSavedMemberIdBeforeExpiry() {
        store.save("token-hash", 42L, Duration.ofMinutes(10));

        assertThat(store.consume("token-hash")).contains(42L);
    }

    @Test
    void consumeIsOneTimeOnlyAndReturnsEmptyOnSecondCall() {
        store.save("token-hash", 42L, Duration.ofMinutes(10));

        store.consume("token-hash");

        assertThat(store.consume("token-hash")).isEmpty();
    }

    @Test
    void consumeReturnsEmptyForAnUnknownToken() {
        assertThat(store.consume("never-saved")).isEmpty();
    }

    @Test
    void consumeReturnsEmptyForAnAlreadyExpiredToken() {
        store.save("token-hash", 42L, Duration.ofSeconds(-1));

        assertThat(store.consume("token-hash")).isEmpty();
    }
}
