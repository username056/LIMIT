package com.c203.limit.domain.chat.event;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.call.event.CallAppointmentUpdatedNotificationEvent;
import com.c203.limit.domain.chat.dto.response.ChatEventResponse;
import com.c203.limit.domain.chat.dto.response.ChatMessageResponse;
import com.c203.limit.domain.chat.service.CallAppointmentChatNotificationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class CallAppointmentUpdatedNotificationEventListenerTests {
    @Test
    void publishesSavedAppointmentNotificationToChatRoom() {
        var notificationService =
                org.mockito.Mockito.mock(CallAppointmentChatNotificationService.class);
        var messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        var listener = new CallAppointmentUpdatedNotificationEventListener(
                notificationService, messagingTemplate);
        var event = new CallAppointmentUpdatedNotificationEvent(
                UUID.randomUUID(),
                10L,
                20L,
                1L,
                LocalDateTime.of(2026, 8, 1, 15, 30),
                "저녁 시간으로 변경");
        var message = new ChatMessageResponse(
                30L,
                4L,
                1L,
                event.eventId(),
                "SYSTEM",
                "검증 약속이 변경됐어요!",
                "SENT",
                LocalDateTime.now(),
                List.of(),
                null,
                null);
        when(notificationService.save(event))
                .thenReturn(new CallAppointmentChatNotificationService.NotificationResult(
                        message, true));

        listener.handle(event);

        verify(messagingTemplate).convertAndSend(
                "/sub/chat-rooms/20",
                ChatEventResponse.message(20L, message));
    }
}
