package com.c203.limit.testsupport;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Provides isolated databases on one MySQL server for the integration-test JVM.
 *
 * <p>CI supplies a MySQL service through environment variables, so the normal pipeline does not
 * need Docker-in-Docker or one container per test class. Local runs transparently fall back to one
 * shared Testcontainers server.
 */
public final class MySqlTestServer {
    private static final Pattern DATABASE_NAME = Pattern.compile("[a-z0-9_]+");
    private static final String LOCAL_PASSWORD = "test-only-password";
    private static final int MYSQL_PORT = 3306;
    private static final Map<String, Database> DATABASES = new ConcurrentHashMap<>();
    private static final Object SERVER_LOCK = new Object();

    private static volatile Server server;
    private static MySQLContainer localContainer;

    private MySqlTestServer() {}

    public static Database database(String name) {
        if (!DATABASE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid test database name: " + name);
        }
        return DATABASES.computeIfAbsent(name, MySqlTestServer::createDatabase);
    }

    private static Database createDatabase(String name) {
        Server current = server();
        SQLException lastFailure = null;
        for (int attempt = 1; attempt <= 60; attempt++) {
            try (var connection = DriverManager.getConnection(
                            current.adminJdbcUrl(), current.username(), current.password());
                    Statement statement = connection.createStatement()) {
                statement.execute(
                        "CREATE DATABASE IF NOT EXISTS `"
                                + name
                                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                return new Database(
                        current.databaseJdbcUrl(name), current.username(), current.password());
            } catch (SQLException exception) {
                lastFailure = exception;
                if (attempt == 60) {
                    break;
                }
                try {
                    Thread.sleep(Duration.ofSeconds(1).toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "interrupted while waiting for the test MySQL server", interrupted);
                }
            }
        }
        throw new IllegalStateException("test MySQL server did not become ready", lastFailure);
    }

    private static Server server() {
        Server current = server;
        if (current != null) {
            return current;
        }
        synchronized (SERVER_LOCK) {
            if (server == null) {
                server = externalServer();
                if (server == null) {
                    server = startLocalServer();
                }
            }
            return server;
        }
    }

    private static Server externalServer() {
        String jdbcBaseUrl = setting("INTEGRATION_MYSQL_JDBC_URL");
        if (jdbcBaseUrl == null) {
            return null;
        }
        String username = requiredSetting("INTEGRATION_MYSQL_USERNAME");
        String password = requiredSetting("INTEGRATION_MYSQL_PASSWORD");
        return new Server(stripTrailingSlash(jdbcBaseUrl), username, password);
    }

    @SuppressWarnings({"rawtypes", "resource"})
    private static Server startLocalServer() {
        localContainer =
                new MySQLContainer(DockerImageName.parse("mysql:8.4"))
                        .withDatabaseName("bootstrap")
                        .withUsername("limit")
                        .withPassword(LOCAL_PASSWORD);
        localContainer.start();
        return new Server(
                "jdbc:mysql://"
                        + localContainer.getHost()
                        + ":"
                        + localContainer.getMappedPort(MYSQL_PORT),
                "root",
                LOCAL_PASSWORD);
    }

    private static String setting(String name) {
        String systemValue = System.getProperty(name);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue.trim();
        }
        String environmentValue = System.getenv(name);
        return environmentValue == null || environmentValue.isBlank()
                ? null
                : environmentValue.trim();
    }

    private static String requiredSetting(String name) {
        String value = setting(name);
        if (value == null) {
            throw new IllegalStateException(name + " is required when external MySQL is configured");
        }
        return value;
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    public record Database(String jdbcUrl, String username, String password) {}

    private record Server(String jdbcBaseUrl, String username, String password) {
        private String adminJdbcUrl() {
            return databaseJdbcUrl("mysql");
        }

        private String databaseJdbcUrl(String database) {
            return jdbcBaseUrl
                    + "/"
                    + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul";
        }
    }
}
