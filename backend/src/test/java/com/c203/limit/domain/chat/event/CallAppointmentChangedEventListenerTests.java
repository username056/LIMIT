package com.c203.limit.domain.chat.event;

import static org.mockito.Mockito.verify;

import com.c203.limit.domain.call.event.CallAppointmentChangedEvent;
import com.c203.limit.domain.chat.dto.response.ChatEventResponse;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class CallAppointmentChangedEventListenerTests {
    @Test
    void broadcastsAppointmentRefreshEvent() {
        SimpMessagingTemplate messagingTemplate =
                org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        var listener = new CallAppointmentChangedEventListener(messagingTemplate);

        listener.handle(new CallAppointmentChangedEvent(20L));

        verify(messagingTemplate)
                .convertAndSend(
                        "/sub/chat-rooms/20",
                        ChatEventResponse.callAppointmentUpdated(20L));
    }
}
