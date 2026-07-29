package com.c203.limit.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.chat.repository.ChatMessageRepository;
import com.c203.limit.domain.chat.repository.ChatOutboxEventRepository;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.chat.repository.ReinspectionRequestMessageRepository;
import com.c203.limit.domain.inspection.event.ReinspectionNotificationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReinspectionChatNotificationServiceTests {

    @Test
    void usesMinimalOutboxPayloadWhenStructuredPayloadSerializationFails()
            throws JsonProcessingException {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialization failed") {});
        ReinspectionChatNotificationService service = new ReinspectionChatNotificationService(
                mock(ChatRoomRepository.class),
                mock(ChatMessageRepository.class),
                mock(ReinspectionRequestMessageRepository.class),
                mock(ChatOutboxEventRepository.class),
                objectMapper);
        UUID eventId = new UUID(1L, 2L);
        ReinspectionNotificationEvent event = new ReinspectionNotificationEvent(
                eventId,
                ReinspectionNotificationEvent.Type.REQUESTED,
                10L,
                "request-key",
                20L,
                30L,
                40L,
                2,
                "사유",
                List.of());

        String payload = ReflectionTestUtils.invokeMethod(service, "serializePayload", event);

        assertThat(payload)
                .contains(eventId.toString())
                .contains("\"reinspectionRequestId\":10")
                .contains("\"serializationFallback\":true");
    }
}
