package com.c203.limit.domain.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ChatWebSocketErrorHandlerTests {

    ObjectMapper objectMapper = new ObjectMapper();
    ChatWebSocketErrorHandler handler = new ChatWebSocketErrorHandler(objectMapper);

    @Test
    void convertsBusinessExceptionToCommonErrorContract() throws Exception {
        Message<byte[]> result = handler.handleClientMessageProcessingError(
                null, new IllegalStateException(
                        new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED)));
        JsonNode body = objectMapper.readTree(
                new String(result.getPayload(), StandardCharsets.UTF_8));

        assertThat(body.path("error").path("code").asText()).isEqualTo("CHT004");
        assertThat(body.path("error").path("message").asText())
                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED.getMessage());
        assertThat(body.path("error").path("fieldErrors").isArray()).isTrue();
        assertThat(body.path("traceId").asText()).isNotBlank();
    }
}
