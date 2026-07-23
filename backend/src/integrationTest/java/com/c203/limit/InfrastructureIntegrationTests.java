package com.c203.limit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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
@Tag("full-infrastructure")
class InfrastructureIntegrationTests {

    @Autowired
    JdbcTemplate jdbcTemplate;

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
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
        assertThat(tableExists("user_account")).isTrue();
        assertThat(tableExists("social_account")).isTrue();
        assertThat(tableExists("member_terms_agreement")).isTrue();
        assertThat(tableExists("admin_account")).isTrue();
        assertThat(tableExists("member_sanction")).isTrue();
        assertThat(tableExists("admin_action_log")).isTrue();
        assertThat(tableExists("flyway_schema_history")).isTrue();
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName);
        return count != null && count == 1;
    }
}
