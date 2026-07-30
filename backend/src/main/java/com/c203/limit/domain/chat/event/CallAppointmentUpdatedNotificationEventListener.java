package com.c203.limit.domain.chat.event;

import com.c203.limit.domain.call.event.CallAppointmentUpdatedNotificationEvent;
import com.c203.limit.domain.chat.dto.response.ChatEventResponse;
import com.c203.limit.domain.chat.service.CallAppointmentChatNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CallAppointmentUpdatedNotificationEventListener {
    private static final Logger log =
            LoggerFactory.getLogger(CallAppointmentUpdatedNotificationEventListener.class);

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
            log.info(
                    "duplicate call appointment notification skipped: eventId={}, roomId={}",
                    event.eventId(),
                    event.chatRoomId());
            return;
        }
        messagingTemplate.convertAndSend(
                "/sub/chat-rooms/" + event.chatRoomId(),
                ChatEventResponse.message(event.chatRoomId(), result.message()));
        log.info(
                "call appointment notification published: eventId={}, roomId={}",
                event.eventId(),
                event.chatRoomId());
    }
}
