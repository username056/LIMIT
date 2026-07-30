package com.c203.limit.testsupport;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** Uses the CI Redis service when configured and one shared Testcontainers instance locally. */
public final class RedisTestServer {
    private static final int REDIS_PORT = 6379;
    private static final Object SERVER_LOCK = new Object();

    private static volatile Endpoint endpoint;
    private static GenericContainer<?> localContainer;

    private RedisTestServer() {}

    public static Endpoint endpoint() {
        Endpoint current = endpoint;
        if (current != null) {
            return current;
        }
        synchronized (SERVER_LOCK) {
            if (endpoint == null) {
                endpoint = externalEndpoint();
                if (endpoint == null) {
                    endpoint = startLocalEndpoint();
                }
            }
            return endpoint;
        }
    }

    private static Endpoint externalEndpoint() {
        String host = setting("INTEGRATION_REDIS_HOST");
        if (host == null) {
            return null;
        }
        String port = setting("INTEGRATION_REDIS_PORT");
        return new Endpoint(host, port == null ? REDIS_PORT : Integer.parseInt(port));
    }

    private static Endpoint startLocalEndpoint() {
        localContainer =
                new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                        .withExposedPorts(REDIS_PORT);
        localContainer.start();
        return new Endpoint(localContainer.getHost(), localContainer.getMappedPort(REDIS_PORT));
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

    public record Endpoint(String host, int port) {}
}
