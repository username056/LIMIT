package com.c203.limit.domain.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.c203.limit.domain.chat.dto.request.ChatMessageSendRequest;
import com.c203.limit.domain.chat.dto.request.ChatReadRequest;
import com.c203.limit.domain.chat.dto.response.ChatAckResponse;
import com.c203.limit.domain.chat.dto.response.ChatEventResponse;
import com.c203.limit.domain.chat.dto.response.ChatMessageResponse;
import com.c203.limit.domain.chat.service.ChatRoomService;
import com.c203.limit.domain.chat.service.ChatRoomService.ChatMessageSendResult;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class ChatMessageWebSocketControllerTests {
    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 20L;
    private static final String ROOM_TOPIC = "/sub/chat-rooms/10";
    private static final String ACK_DESTINATION = "/queue/chat-acks";

    @Mock ChatRoomService chatRoomService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock ChatWebSocketErrorPublisher errorPublisher;

    ChatMessageWebSocketController controller;
    AuthenticatedUser member;

    @BeforeEach
    void setUp() {
        controller =
                new ChatMessageWebSocketController(
                        chatRoomService, messagingTemplate, errorPublisher);
        member = new AuthenticatedUser(MEMBER_ID, "MEMBER", Set.of("MEMBER"));
    }

    @Test
    void sendMessageAcknowledgesSenderAndBroadcastsNewlyCreatedMessage() {
        ChatMessageSendRequest request = sendRequest();
        ChatMessageResponse message = messageResponse(request.clientMessageId());
        when(chatRoomService.sendMessage(ROOM_ID, MEMBER_ID, request))
                .thenReturn(new ChatMessageSendResult(message, true));

        controller.sendMessage(ROOM_ID, request, member);

        ArgumentCaptor<ChatAckResponse> ackCaptor =
                ArgumentCaptor.forClass(ChatAckResponse.class);
        verify(messagingTemplate)
                .convertAndSendToUser(
                        eq("MEMBER:20"), eq(ACK_DESTINATION), ackCaptor.capture());
        assertThat(ackCaptor.getValue().messageId()).isEqualTo(100L);
        assertThat(ackCaptor.getValue().roomSequence()).isEqualTo(7L);
        assertThat(ackCaptor.getValue().status()).isEqualTo("SENT");
        assertThat(ackCaptor.getValue().clientMessageId())
                .isEqualTo(request.clientMessageId());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq(ROOM_TOPIC), eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isInstanceOfSatisfying(
                        ChatEventResponse.class,
                        event -> {
                            assertThat(event.type()).isEqualTo("MESSAGE");
                            assertThat(event.roomId()).isEqualTo(ROOM_ID);
                            assertThat(event.message()).isSameAs(message);
                        });
        verifyNoInteractions(errorPublisher);
    }

    @Test
    void sendMessageOnlyAcknowledgesSenderWhenMessageWasAlreadyStored() {
        ChatMessageSendRequest request = sendRequest();
        ChatMessageResponse message = messageResponse(request.clientMessageId());
        when(chatRoomService.sendMessage(ROOM_ID, MEMBER_ID, request))
                .thenReturn(new ChatMessageSendResult(message, false));

        controller.sendMessage(ROOM_ID, request, member);

        verify(messagingTemplate)
                .convertAndSendToUser(
                        eq("MEMBER:20"),
                        eq(ACK_DESTINATION),
                        org.mockito.ArgumentMatchers.any(ChatAckResponse.class));
        verifyNoMoreInteractions(messagingTemplate);
        verifyNoInteractions(errorPublisher);
    }

    @Test
    void sendMessagePublishesBusinessErrorToSenderInsteadOfBroadcasting() {
        ChatMessageSendRequest request = sendRequest();
        BusinessException failure = new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        when(chatRoomService.sendMessage(ROOM_ID, MEMBER_ID, request)).thenThrow(failure);

        controller.sendMessage(ROOM_ID, request, member);

        verify(errorPublisher).publish(member, failure);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void sendMessageRejectsUnauthenticatedSession() {
        assertThatThrownBy(() -> controller.sendMessage(ROOM_ID, sendRequest(), null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(chatRoomService, messagingTemplate, errorPublisher);
    }

    @Test
    void sendMessageRejectsPrincipalThatIsNotAnAuthenticatedUser() {
        Principal foreignPrincipal = () -> "anonymous";

        assertThatThrownBy(
                        () -> controller.sendMessage(ROOM_ID, sendRequest(), foreignPrincipal))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(chatRoomService, messagingTemplate, errorPublisher);
    }

    @Test
    void sendMessageRejectsAdminAccountType() {
        AuthenticatedUser admin = new AuthenticatedUser(99L, "ADMIN", Set.of("SUPER_ADMIN"));

        assertThatThrownBy(() -> controller.sendMessage(ROOM_ID, sendRequest(), admin))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(chatRoomService, messagingTemplate, errorPublisher);
    }

    @Test
    void readMessagesBroadcastsReadEventWithLastReadSequence() {
        ChatReadRequest request = new ChatReadRequest(12L);
        when(chatRoomService.readMessages(ROOM_ID, MEMBER_ID, request)).thenReturn(12L);

        controller.readMessages(ROOM_ID, request, member);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq(ROOM_TOPIC), eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isInstanceOfSatisfying(
                        ChatEventResponse.class,
                        event -> {
                            assertThat(event.type()).isEqualTo("READ");
                            assertThat(event.roomId()).isEqualTo(ROOM_ID);
                            assertThat(event.readerId()).isEqualTo(MEMBER_ID);
                            assertThat(event.lastReadSeq()).isEqualTo(12L);
                            assertThat(event.message()).isNull();
                        });
        verifyNoInteractions(errorPublisher);
    }

    @Test
    void readMessagesPublishesBusinessErrorToSenderInsteadOfBroadcasting() {
        ChatReadRequest request = new ChatReadRequest(12L);
        BusinessException failure = new BusinessException(ErrorCode.LISTING_NOT_FOUND);
        when(chatRoomService.readMessages(ROOM_ID, MEMBER_ID, request)).thenThrow(failure);

        controller.readMessages(ROOM_ID, request, member);

        verify(errorPublisher).publish(member, failure);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void readMessagesRejectsUnauthenticatedSession() {
        assertThatThrownBy(
                        () -> controller.readMessages(ROOM_ID, new ChatReadRequest(1L), null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(chatRoomService, messagingTemplate, errorPublisher);
    }

    @Test
    void readMessagesRejectsAdminAccountType() {
        AuthenticatedUser admin = new AuthenticatedUser(99L, "ADMIN", Set.of("OPERATOR"));

        assertThatThrownBy(
                        () -> controller.readMessages(ROOM_ID, new ChatReadRequest(1L), admin))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(chatRoomService, messagingTemplate, errorPublisher);
    }

    private ChatMessageSendRequest sendRequest() {
        return new ChatMessageSendRequest(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "TEXT",
                "안녕하세요",
                List.of());
    }

    private ChatMessageResponse messageResponse(UUID clientMessageId) {
        return new ChatMessageResponse(
                100L,
                7L,
                MEMBER_ID,
                clientMessageId,
                "TEXT",
                "안녕하세요",
                "SENT",
                LocalDateTime.of(2026, 1, 2, 3, 4, 5),
                List.of(),
                null,
                null);
    }
}
