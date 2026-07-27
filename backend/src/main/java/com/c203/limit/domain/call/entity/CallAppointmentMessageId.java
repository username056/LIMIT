package com.c203.limit.domain.call.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record CallAppointmentMessageId(
        @Column(name = "call_appointment_id") Long callAppointmentId,
        @Column(name = "chat_message_id") Long chatMessageId)
        implements Serializable {

    public CallAppointmentMessageId() {
        this(null, null);
    }
}
