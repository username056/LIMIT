package com.c203.limit.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@IdClass(ReinspectionRequestMessageId.class)
@Table(name = "reinspection_request_message")
public class ReinspectionRequestMessage {
    @Id
    @Column(name = "reinspection_request_id", nullable = false)
    private Long reinspectionRequestId;

    @Id
    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "chat_message_id", nullable = false)
    private Long chatMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ReinspectionRequestMessage() {}

    public static ReinspectionRequestMessage link(
            Long reinspectionRequestId,
            String eventType,
            Long chatMessageId,
            LocalDateTime createdAt) {
        ReinspectionRequestMessage link = new ReinspectionRequestMessage();
        link.reinspectionRequestId = reinspectionRequestId;
        link.eventType = eventType;
        link.chatMessageId = chatMessageId;
        link.createdAt = createdAt;
        return link;
    }
}
