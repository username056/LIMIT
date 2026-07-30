package com.c203.limit.domain.chat.event;

import com.c203.limit.domain.call.event.CallAppointmentChangedEvent;
import com.c203.limit.domain.chat.dto.response.ChatEventResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CallAppointmentChangedEventListener {
    private final SimpMessagingTemplate messagingTemplate;

    public CallAppointmentChangedEventListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(CallAppointmentChangedEvent event) {
        messagingTemplate.convertAndSend(
                "/sub/chat-rooms/" + event.chatRoomId(),
                ChatEventResponse.callAppointmentUpdated(event.chatRoomId()));
    }
}
