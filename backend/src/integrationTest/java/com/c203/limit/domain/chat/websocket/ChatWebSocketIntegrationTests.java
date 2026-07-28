package com.c203.limit.domain.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.lang.reflect.Type;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import com.c203.limit.domain.chat.dto.response.ChatEventResponse;
import com.c203.limit.domain.chat.dto.response.ChatMessageResponse;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.domain.chat.service.ChatRoomService;
import com.c203.limit.domain.chat.service.ChatRoomService.ChatMessageSendResult;
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
        ChatWebSocketErrorPublisher.class,
        ChatMessageWebSocketController.class
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
    @MockitoBean ChatRoomService chatRoomService;

    WebSocketStompClient stompClient;
    StompSession session;

    @BeforeEach
    void setUp() throws Exception {
        when(tokenProvider.parse("access-token", "access"))
                .thenReturn(
                        new TokenClaims(
                                "token-id", MEMBER_ID, "MEMBER", Set.of("MEMBER")));
        when(tokenProvider.parse("buyer-token", "access"))
                .thenReturn(new TokenClaims("buyer-jti", MEMBER_ID, "MEMBER", Set.of("MEMBER")));
        when(tokenProvider.parse("seller-token", "access"))
                .thenReturn(new TokenClaims("seller-jti", 11L, "MEMBER", Set.of("MEMBER")));

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());

        session = connect("access-token");
    }

    private StompSession connect(String accessToken) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + accessToken);
        return stompClient
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

    @Test
    void broadcastsMessageBetweenTwoAuthenticatedMembers() throws Exception {
        StompSession buyer = connect("buyer-token");
        StompSession seller = connect("seller-token");
        try {
            when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(ROOM_ID, MEMBER_ID))
                    .thenReturn(true);
            CompletableFuture<Void> subscribed = new CompletableFuture<>();
            when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(ROOM_ID, 11L))
                    .thenAnswer(invocation -> {
                        subscribed.complete(null);
                        return true;
                    });
            UUID clientMessageId = UUID.randomUUID();
            ChatMessageResponse message = new ChatMessageResponse(
                    501L, 1L, MEMBER_ID, clientMessageId, "TEXT", "안녕하세요",
                    "SENT", null, List.of());
            when(chatRoomService.sendMessage(eq(ROOM_ID), eq(MEMBER_ID), any()))
                    .thenReturn(new ChatMessageSendResult(message, true));

            CompletableFuture<ChatEventResponse> received = new CompletableFuture<>();
            seller.subscribe(
                    "/sub/chat-rooms/" + ROOM_ID,
                    new StompFrameHandler() {
                        @Override
                        public Type getPayloadType(StompHeaders headers) {
                            return ChatEventResponse.class;
                        }

                        @Override
                        public void handleFrame(StompHeaders headers, Object payload) {
                            received.complete((ChatEventResponse) payload);
                        }
                    });
            subscribed.get(5, TimeUnit.SECONDS);

            buyer.send(
                    "/pub/chat-rooms/" + ROOM_ID + "/messages",
                    Map.of(
                            "clientMessageId", clientMessageId.toString(),
                            "type", "TEXT",
                            "content", "안녕하세요",
                            "mediaIds", List.of()));

            ChatEventResponse event = received.get(5, TimeUnit.SECONDS);
            assertThat(event.type()).isEqualTo("MESSAGE");
            assertThat(event.message().senderId()).isEqualTo(MEMBER_ID);
            assertThat(event.message().content()).isEqualTo("안녕하세요");
        } finally {
            buyer.disconnect();
            seller.disconnect();
        }
    }
}
