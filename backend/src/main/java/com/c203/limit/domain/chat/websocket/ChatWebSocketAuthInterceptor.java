package com.c203.limit.domain.chat.websocket;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.AuthenticatedUser;
import com.c203.limit.global.security.JwtTokenProvider;
import com.c203.limit.global.security.JwtTokenProvider.TokenClaims;

@Component
public class ChatWebSocketAuthInterceptor implements ChannelInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ERROR_DESTINATION = "/user/queue/errors";
    private static final String USER_ACK_DESTINATION = "/user/queue/chat-acks";
    private static final Pattern CHAT_ROOM_DESTINATION =
            Pattern.compile("^/sub/chat-rooms/([1-9][0-9]*)$");

    private final JwtTokenProvider tokenProvider;
    private final ChatRoomParticipantRepository participantRepository;
    private final ChatWebSocketErrorPublisher errorPublisher;

    public ChatWebSocketAuthInterceptor(
            JwtTokenProvider tokenProvider,
            ChatRoomParticipantRepository participantRepository,
            ChatWebSocketErrorPublisher errorPublisher) {
        this.tokenProvider = tokenProvider;
        this.participantRepository = participantRepository;
        this.errorPublisher = errorPublisher;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return authorizeSubscriptionOrReport(message, accessor);
        }
        return message;
    }

    private Message<?> authorizeSubscriptionOrReport(
            Message<?> message, StompHeaderAccessor accessor) {
        try {
            authorizeSubscription(accessor);
            return message;
        } catch (BusinessException exception) {
            if (accessor.getUser() instanceof AuthenticatedUser user) {
                errorPublisher.publish(user, exception);
                return null;
            }
            throw exception;
        }
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)
                || authorization.length() == BEARER_PREFIX.length()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        TokenClaims claims =
                tokenProvider.parse(authorization.substring(BEARER_PREFIX.length()), "access");
        if (!"MEMBER".equals(claims.accountType())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        accessor.setUser(
                new AuthenticatedUser(claims.subjectId(), claims.accountType(), claims.roles()));
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        AuthenticatedUser user = authenticatedMember(accessor);
        String destination = accessor.getDestination();
        if (USER_ERROR_DESTINATION.equals(destination) || USER_ACK_DESTINATION.equals(destination)) {
            return;
        }

        Matcher matcher = CHAT_ROOM_DESTINATION.matcher(
                destination == null ? "" : destination);
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }

        Long roomId = Long.valueOf(matcher.group(1));
        if (!participantRepository.existsByChatRoomIdAndUserIdAndLeftAtIsNull(
                roomId, user.id())) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }
    }

    private AuthenticatedUser authenticatedMember(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof AuthenticatedUser user)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!"MEMBER".equals(user.accountType())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }
}
