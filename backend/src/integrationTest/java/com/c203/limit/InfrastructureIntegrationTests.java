package com.c203.limit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class InfrastructureIntegrationTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("limit")
            .withUsername("limit")
            .withPassword("test-only-password");

    @Container
    static final MongoDBContainer MONGODB = new MongoDBContainer(DockerImageName.parse("mongo:8.0"));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Container
    static final GenericContainer<?> QDRANT = new GenericContainer<>(DockerImageName.parse("qdrant/qdrant:v1.14.1"))
            .withExposedPorts(6333);

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("limit.qdrant.url", () -> "http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(6333));
    }

    @Test
    void startsRealInfrastructureEngines() {
        assertThat(MYSQL.isRunning()).isTrue();
        assertThat(MONGODB.isRunning()).isTrue();
        assertThat(REDIS.isRunning()).isTrue();
        assertThat(QDRANT.isRunning()).isTrue();
    }
}
