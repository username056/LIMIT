package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryAuthRateLimitStoreTests {
    @Test
    void rejectsAttemptsAfterConfiguredLimit() {
        var store = new InMemoryAuthRateLimitStore();

        assertThat(store.tryAcquire("email:key", 2, Duration.ofMinutes(10))).isTrue();
        assertThat(store.tryAcquire("email:key", 2, Duration.ofMinutes(10))).isTrue();
        assertThat(store.tryAcquire("email:key", 2, Duration.ofMinutes(10))).isFalse();
        assertThat(store.tryAcquire("email:other", 2, Duration.ofMinutes(10))).isTrue();
    }
}
