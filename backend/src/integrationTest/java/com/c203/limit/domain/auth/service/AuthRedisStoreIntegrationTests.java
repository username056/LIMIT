package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.testsupport.RedisTestServer;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class AuthRedisStoreIntegrationTests {
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        RedisTestServer.Endpoint endpoint = RedisTestServer.endpoint();
        var configuration =
                new RedisStandaloneConfiguration(endpoint.host(), endpoint.port());
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void enforcesDistributedRateLimitAndExpiresWindow() throws InterruptedException {
        var store = new RedisAuthRateLimitStore(redis);
        String key = "integration:" + UUID.randomUUID();

        assertThat(store.tryAcquire(key, 2, Duration.ofMillis(200))).isTrue();
        assertThat(store.tryAcquire(key, 2, Duration.ofMillis(200))).isTrue();
        assertThat(store.tryAcquire(key, 2, Duration.ofMillis(200))).isFalse();

        Thread.sleep(250);

        assertThat(store.tryAcquire(key, 2, Duration.ofMillis(200))).isTrue();
    }

    @Test
    void consumesPasswordResetTokenOnlyOnce() {
        var store = new RedisPasswordResetTokenStore(redis);
        String tokenHash = "integration-" + UUID.randomUUID();

        store.save(tokenHash, 42L, Duration.ofMinutes(1));

        assertThat(store.consume(tokenHash)).contains(42L);
        assertThat(store.consume(tokenHash)).isEmpty();
    }
}
