package com.c203.limit.domain.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.c203.limit.domain.chat.config.ChatWebSocketConfig;
import com.c203.limit.domain.chat.dto.response.WebSocketErrorResponse;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.global.security.JwtTokenProvider;
import com.c203.limit.global.security.JwtTokenProvider.TokenClaims;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(
        classes = ChatWebSocketIntegrationTests.TestApplication.class,
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                    + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                    + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                    + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                    + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,"
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration",
            "limit.websocket.allowed-origin-patterns=http://localhost:*"
        })
class ChatWebSocketIntegrationTests {
    private static final long MEMBER_ID = 10L;
    private static final long ROOM_ID = 20L;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
        ChatWebSocketConfig.class,
        ChatWebSocketAuthInterceptor.class,
        ChatWebSocketErrorHandler.class,
        ChatWebSocketErrorPublisher.class
    })
    static class TestApplication {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable());
            http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
            return http.build();
        }
    }

    @LocalServerPort int port;

    @MockitoBean JwtTokenProvider tokenProvider;
    @MockitoBean ChatRoomParticipantRepository participantRepository;

    WebSocketStompClient stompClient;
    StompSession session;

    @BeforeEach
    void setUp() throws Exception {
        when(tokenProvider.parse("access-token", "access"))
                .thenReturn(
                        new TokenClaims(
                                "token-id", MEMBER_ID, "MEMBER", Set.of("MEMBER")));

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer access-token");
        session = stompClient
                .connectAsync(
                        "ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    void sendsSubscriptionAuthorizationFailureToPersonalErrorQueue() throws Exception {
        CompletableFuture<WebSocketErrorResponse> errorFuture = new CompletableFuture<>();
        session.subscribe(
                "/user/queue/errors",
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return WebSocketErrorResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        errorFuture.complete((WebSocketErrorResponse) payload);
                    }
                });

        session.subscribe(
                "/sub/chat-rooms/" + ROOM_ID,
                new StompSessionHandlerAdapter() {});

        WebSocketErrorResponse response = errorFuture.get(5, TimeUnit.SECONDS);

        assertThat(response.error().code()).isEqualTo("CHT004");
        assertThat(response.traceId()).isNotBlank();
        assertThat(session.isConnected()).isTrue();
    }
}
