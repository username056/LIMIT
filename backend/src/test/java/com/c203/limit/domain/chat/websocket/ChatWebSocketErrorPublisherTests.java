package com.c203.limit.domain.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.c203.limit.domain.chat.dto.response.WebSocketErrorResponse;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketErrorPublisherTests {

    @Mock ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;
    @Mock SimpMessagingTemplate messagingTemplate;

    @Test
    void sendsCommonErrorResponseToAuthenticatedUserQueue() {
        ChatWebSocketErrorPublisher publisher =
                new ChatWebSocketErrorPublisher(messagingTemplateProvider);
        AuthenticatedUser user =
                new AuthenticatedUser(10L, "MEMBER", Set.of("MEMBER"));
        ArgumentCaptor<WebSocketErrorResponse> responseCaptor =
                ArgumentCaptor.forClass(WebSocketErrorResponse.class);
        org.mockito.Mockito.when(messagingTemplateProvider.getObject())
                .thenReturn(messagingTemplate);

        publisher.publish(
                user, new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));

        verify(messagingTemplate)
                .convertAndSendToUser(
                        eq(user.getName()), eq("/queue/errors"), responseCaptor.capture());
        WebSocketErrorResponse response = responseCaptor.getValue();
        assertThat(response.error().code()).isEqualTo("CHT004");
        assertThat(response.error().message())
                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED.getMessage());
        assertThat(response.error().fieldErrors()).isEmpty();
        assertThat(response.traceId()).isNotBlank();
    }
}
