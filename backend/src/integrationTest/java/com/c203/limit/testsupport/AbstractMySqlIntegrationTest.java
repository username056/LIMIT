package com.c203.limit.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Shares one application database and one Spring test-context cache key across integration tests. */
public abstract class AbstractMySqlIntegrationTest {
    private static final MySqlTestServer.Database DATABASE =
            MySqlTestServer.database("limit_application");

    @DynamicPropertySource
    protected static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::jdbcUrl);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
        registry.add(
                "limit.payment.reservation-expiration.initial-delay-ms",
                () -> "999999999");
    }
}
