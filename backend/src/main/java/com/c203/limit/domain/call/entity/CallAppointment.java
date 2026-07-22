package com.c203.limit.domain.call.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.c203.limit.domain.call.domain.AppointmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "call_appointment", indexes = {
        @Index(name = "IX_CALL_APPOINTMENT_ROOM_SCHEDULE", columnList = "chat_room_id,scheduled_at")
})
public class CallAppointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_key", nullable = false, unique = true)
    private UUID appointmentKey;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "proposer_id", nullable = false)
    private Long proposerId;

    @Column(name = "respondent_id", nullable = false)
    private Long respondentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(length = 500)
    private String memo;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected CallAppointment() {}
}
