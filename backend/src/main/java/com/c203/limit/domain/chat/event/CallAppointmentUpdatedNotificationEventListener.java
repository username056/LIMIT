package com.c203.limit.domain.chat.event;

import com.c203.limit.domain.call.event.CallAppointmentUpdatedNotificationEvent;
import com.c203.limit.domain.chat.dto.response.ChatEventResponse;
import com.c203.limit.domain.chat.service.CallAppointmentChatNotificationService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CallAppointmentUpdatedNotificationEventListener {
    private final CallAppointmentChatNotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    public CallAppointmentUpdatedNotificationEventListener(
            CallAppointmentChatNotificationService notificationService,
            SimpMessagingTemplate messagingTemplate) {
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(CallAppointmentUpdatedNotificationEvent event) {
        var result = notificationService.save(event);
        if (!result.created()) {
            return;
        }
        messagingTemplate.convertAndSend(
                "/sub/chat-rooms/" + event.chatRoomId(),
                ChatEventResponse.message(event.chatRoomId(), result.message()));
    }
}
