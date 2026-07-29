package com.c203.limit.domain.chat.event;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.chat.dto.response.ChatMessageResponse;
import com.c203.limit.domain.chat.dto.response.ChatEventResponse;
import com.c203.limit.domain.chat.service.ReinspectionChatNotificationService;
import com.c203.limit.domain.chat.service.ReinspectionChatNotificationService.NotificationResult;
import com.c203.limit.domain.inspection.event.ReinspectionNotificationEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class ReinspectionNotificationEventListenerTests {
    @Mock ReinspectionChatNotificationService notificationService;
    @Mock SimpMessagingTemplate messagingTemplate;

    @Test
    void broadcastsRequestedEventAfterNewSystemMessageIsCommitted() {
        ReinspectionNotificationEvent event = event(ReinspectionNotificationEvent.Type.REQUESTED);
        ChatMessageResponse message = message(event.eventId());
        when(notificationService.save(event)).thenReturn(new NotificationResult(message, 99L, true));
        var listener =
                new ReinspectionNotificationEventListener(notificationService, messagingTemplate);

        listener.handle(event);

        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/sub/chat-rooms/20"),
                org.mockito.ArgumentMatchers.<Object>argThat(response -> {
                    ChatEventResponse eventResponse = (ChatEventResponse) response;
                    return "REINSPECTION_REQUESTED".equals(eventResponse.type())
                            && message.equals(eventResponse.message());
                }));
        verify(notificationService).markPublished(99L);
    }

    @Test
    void doesNotBroadcastDuplicateEvent() {
        ReinspectionNotificationEvent event = event(ReinspectionNotificationEvent.Type.COMPLETED);
        when(notificationService.save(event))
                .thenReturn(new NotificationResult(message(event.eventId()), null, false));
        var listener =
                new ReinspectionNotificationEventListener(notificationService, messagingTemplate);

        listener.handle(event);

        verifyNoInteractions(messagingTemplate);
    }

    private ReinspectionNotificationEvent event(ReinspectionNotificationEvent.Type type) {
        return new ReinspectionNotificationEvent(
                new UUID(1L, 2L),
                type,
                10L,
                "request-key",
                20L,
                30L,
                40L,
                2,
                "사유",
                List.of(new ReinspectionNotificationEvent.Item("외관", "확대")));
    }

    private ChatMessageResponse message(UUID eventId) {
        return new ChatMessageResponse(
                1L,
                1L,
                30L,
                eventId,
                "SYSTEM",
                "재검수",
                "SENT",
                LocalDateTime.of(2026, 7, 29, 10, 0),
                List.of());
    }
}
