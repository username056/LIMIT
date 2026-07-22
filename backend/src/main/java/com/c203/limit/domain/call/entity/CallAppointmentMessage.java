package com.c203.limit.domain.call.entity;

import java.time.LocalDateTime;

import com.c203.limit.domain.call.domain.AppointmentEvent;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "call_appointment_message")
public class CallAppointmentMessage {
    @EmbeddedId
    private CallAppointmentMessageId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_event", nullable = false, length = 20)
    private AppointmentEvent event;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CallAppointmentMessage() {}
}
