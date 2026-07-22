package com.c203.limit.domain.call.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record CallAppointmentMessageId(
        @Column(name = "call_appointment_id") Long callAppointmentId,
        @Column(name = "chat_message_id") Long chatMessageId) implements Serializable {

    public CallAppointmentMessageId() {
        this(null, null);
    }
}
