package com.c203.limit.domain.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.AuthenticatedUser;
import com.c203.limit.global.security.JwtTokenProvider;
import com.c203.limit.global.security.JwtTokenProvider.TokenClaims;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketAuthInterceptorTests {
    private static final Long MEMBER_ID = 10L;
    private static final Long ROOM_ID = 20L;

    @Mock JwtTokenProvider tokenProvider;
    @Mock ChatRoomParticipantRepository participantRepository;
    @Mock ChatWebSocketErrorPublisher errorPublisher;
    @Mock MessageChannel channel;

    ChatWebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor =
                new ChatWebSocketAuthInterceptor(
                        tokenProvider, participantRepository, errorPublisher);
    }

    @Test
    void authenticatesMemberOnConnect() {
        when(tokenProvider.parse("access-token", "access"))
                .thenReturn(new TokenClaims("token-id", MEMBER_ID, "MEMBER", Set.of("MEMBER")));
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer access-token");

        interceptor.preSend(message(accessor), channel);

        assertThat(accessor.getUser())
                .isEqualTo(new AuthenticatedUser(MEMBER_ID, "MEMBER", Set.of("MEMBER")));
    }

    @Test
    void rejectsConnectWithoutBearerToken() {
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(tokenProvider);
    }

    @Test
    void rejectsNonMemberAccountOnConnect() {
        when(tokenProvider.parse("admin-token", "access"))
                .thenReturn(
                        new TokenClaims(
                                "token-id", MEMBER_ID, "ADMIN", Set.of("SUPER_ADMIN")));
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer admin-token");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void allowsParticipantToSubscribeToChatRoom() {
        when(participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(
                        ROOM_ID, MEMBER_ID))
                .thenReturn(true);
        StompHeaderAccessor accessor = authenticatedSubscribe(
                "/sub/chat-rooms/" + ROOM_ID);

        Message<?> result = interceptor.preSend(message(accessor), channel);

        assertThat(result).isNotNull();
    }

    @Test
    void rejectsNonParticipantChatRoomSubscription() {
        StompHeaderAccessor accessor = authenticatedSubscribe(
                "/sub/chat-rooms/" + ROOM_ID);

        Message<?> result = interceptor.preSend(message(accessor), channel);

        assertThat(result).isNull();
        verify(errorPublisher)
                .publish(
                        eq(new AuthenticatedUser(MEMBER_ID, "MEMBER", Set.of("MEMBER"))),
                        argThat(
                                exception ->
                                        exception.getErrorCode()
                                                == ErrorCode.CHAT_ROOM_ACCESS_DENIED));
    }

    @Test
    void allowsAuthenticatedMemberToSubscribeToPersonalErrors() {
        StompHeaderAccessor accessor = authenticatedSubscribe("/user/queue/errors");

        Message<?> result = interceptor.preSend(message(accessor), channel);

        assertThat(result).isNotNull();
        verifyNoInteractions(participantRepository);
    }

    @Test
    void rejectsUnexpectedSubscriptionDestinationThroughPersonalErrors() {
        AuthenticatedUser user =
                new AuthenticatedUser(MEMBER_ID, "MEMBER", Set.of("MEMBER"));
        StompHeaderAccessor accessor = authenticatedSubscribe("/sub/other");

        Message<?> result = interceptor.preSend(message(accessor), channel);

        assertThat(result).isNull();
        verify(errorPublisher)
                .publish(
                        eq(user),
                        argThat(
                                exception ->
                                        exception.getErrorCode()
                                                == ErrorCode.CHAT_ROOM_ACCESS_DENIED));
        verifyNoInteractions(participantRepository);
    }

    private StompHeaderAccessor authenticatedSubscribe(String destination) {
        StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new AuthenticatedUser(MEMBER_ID, "MEMBER", Set.of("MEMBER")));
        return accessor;
    }

    private StompHeaderAccessor accessor(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
