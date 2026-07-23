package com.c203.limit.domain.chat.websocket;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.c203.limit.domain.chat.dto.response.WebSocketErrorResponse;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.security.AuthenticatedUser;

@Component
public class ChatWebSocketErrorPublisher {
    private static final String ERROR_DESTINATION = "/queue/errors";

    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

    public ChatWebSocketErrorPublisher(
            ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider) {
        this.messagingTemplateProvider = messagingTemplateProvider;
    }

    public void publish(AuthenticatedUser user, BusinessException exception) {
        WebSocketErrorResponse response = WebSocketErrorResponse.of(
                exception.getErrorCode(), exception.getMessage(), UUID.randomUUID().toString());
        messagingTemplateProvider
                .getObject()
                .convertAndSendToUser(user.getName(), ERROR_DESTINATION, response);
    }
}
