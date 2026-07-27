package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class AuthRedisStoreIntegrationTests {
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        var configuration =
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
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
