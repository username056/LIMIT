package com.c203.limit.domain.call.entity;

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
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "call_appointment",
        indexes = {
            @Index(
                    name = "IX_CALL_APPOINTMENT_ROOM_SCHEDULE",
                    columnList = "chat_room_id,scheduled_at")
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

    public static CallAppointment propose(
            Long chatRoomId,
            Long proposerId,
            Long respondentId,
            LocalDateTime scheduledAt,
            String memo) {
        CallAppointment appointment = new CallAppointment();
        appointment.appointmentKey = UUID.randomUUID();
        appointment.chatRoomId = chatRoomId;
        appointment.proposerId = proposerId;
        appointment.respondentId = respondentId;
        appointment.status = AppointmentStatus.PROPOSED;
        appointment.scheduledAt = scheduledAt;
        appointment.memo = memo;
        appointment.version = 0L;
        return appointment;
    }

    public void accept(Long memberId) {
        requireRespondent(memberId);
        requireProposed();
        status = AppointmentStatus.ACCEPTED;
        respondedAt = LocalDateTime.now();
    }

    public void reject(Long memberId, String reason) {
        requireRespondent(memberId);
        requireProposed();
        status = AppointmentStatus.REJECTED;
        cancelReason = reason;
        respondedAt = LocalDateTime.now();
    }

    public void update(Long memberId, LocalDateTime scheduledAt, String memo) {
        requireProposer(memberId);
        requireProposed();
        this.scheduledAt = scheduledAt;
        this.memo = memo;
    }

    public void cancel(Long memberId, String reason) {
        requireProposer(memberId);
        requireProposed();
        status = AppointmentStatus.CANCELED;
        cancelReason = reason;
        canceledAt = LocalDateTime.now();
    }

    public boolean isParticipant(Long memberId) {
        return proposerId.equals(memberId) || respondentId.equals(memberId);
    }

    private void requireRespondent(Long memberId) {
        if (!respondentId.equals(memberId))
            throw new IllegalStateException("only respondent can respond");
    }

    private void requireProposer(Long memberId) {
        if (!proposerId.equals(memberId))
            throw new IllegalStateException("only proposer can update appointment");
    }

    private void requireProposed() {
        if (status != AppointmentStatus.PROPOSED) {
            throw new IllegalStateException("appointment is not proposed");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getChatRoomId() {
        return chatRoomId;
    }

    public Long getProposerId() {
        return proposerId;
    }

    public Long getRespondentId() {
        return respondentId;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public String getMemo() {
        return memo;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
}
