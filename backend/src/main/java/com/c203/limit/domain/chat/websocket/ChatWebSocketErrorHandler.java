package com.c203.limit.domain.chat.websocket;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import com.c203.limit.domain.chat.dto.response.WebSocketErrorResponse;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ChatWebSocketErrorHandler extends StompSubProtocolErrorHandler {
    private final ObjectMapper objectMapper;

    public ChatWebSocketErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Message<byte[]> handleClientMessageProcessingError(
            Message<byte[]> clientMessage, Throwable exception) {
        BusinessException businessException = findBusinessException(exception);
        ErrorCode errorCode = businessException == null
                ? ErrorCode.INTERNAL_ERROR
                : businessException.getErrorCode();
        String message = businessException == null
                ? errorCode.getMessage()
                : businessException.getMessage();
        String traceId = UUID.randomUUID().toString();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(message);
        accessor.setContentType(org.springframework.util.MimeTypeUtils.APPLICATION_JSON);
        return MessageBuilder.createMessage(
                serialize(WebSocketErrorResponse.of(errorCode, message, traceId)),
                accessor.getMessageHeaders());
    }

    private BusinessException findBusinessException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof BusinessException businessException) {
                return businessException;
            }
            current = current.getCause();
        }
        return null;
    }

    private byte[] serialize(WebSocketErrorResponse response) {
        try {
            return objectMapper.writeValueAsBytes(response);
        } catch (JsonProcessingException exception) {
            String fallback =
                    "{\"error\":{\"code\":\"CMN002\",\"message\":\"Internal server error\","
                            + "\"fieldErrors\":[]},\"traceId\":\""
                            + response.traceId()
                            + "\"}";
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }
}
