package com.c203.limit.domain.chat.event;

import com.c203.limit.domain.chat.dto.response.ChatEventResponse;
import com.c203.limit.domain.chat.dto.response.ReinspectionNotificationResponse;
import com.c203.limit.domain.chat.service.ReinspectionChatNotificationService;
import com.c203.limit.domain.inspection.event.ReinspectionNotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ReinspectionNotificationEventListener {
    private static final Logger log =
            LoggerFactory.getLogger(ReinspectionNotificationEventListener.class);

    private final ReinspectionChatNotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    public ReinspectionNotificationEventListener(
            ReinspectionChatNotificationService notificationService,
            SimpMessagingTemplate messagingTemplate) {
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ReinspectionNotificationEvent event) {
        var result = notificationService.save(event);
        if (!result.created()) {
            log.info(
                    "duplicate reinspection notification skipped: eventId={}, type={}, roomId={}",
                    event.eventId(),
                    event.type(),
                    event.chatRoomId());
            return;
        }
        ChatEventResponse response =
                event.type() == ReinspectionNotificationEvent.Type.REQUESTED
                        ? ChatEventResponse.reinspectionRequested(
                                event.chatRoomId(),
                                result.message(),
                                ReinspectionNotificationResponse.from(event))
                        : ChatEventResponse.reinspectionCompleted(
                                event.chatRoomId(),
                                result.message(),
                                ReinspectionNotificationResponse.from(event));
        messagingTemplate.convertAndSend(
                "/sub/chat-rooms/" + event.chatRoomId(), response);
        notificationService.markPublished(result.outboxEventId());
        log.info(
                "reinspection notification published: eventId={}, type={}, roomId={}, recipientId={}",
                event.eventId(),
                event.type(),
                event.chatRoomId(),
                event.recipientId());
    }
}
