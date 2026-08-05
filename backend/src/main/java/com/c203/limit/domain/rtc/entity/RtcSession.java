package com.c203.limit.domain.rtc.entity;

import com.c203.limit.domain.rtc.domain.ConnectionType;
import com.c203.limit.domain.rtc.domain.MediaDirection;
import com.c203.limit.domain.rtc.domain.RtcEndReason;
import com.c203.limit.domain.rtc.domain.RtcSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rtc_session")
public class RtcSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_key", nullable = false, unique = true)
    private UUID sessionKey;

    @Column(name = "call_appointment_id")
    private Long callAppointmentId;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_direction", nullable = false, length = 30)
    private MediaDirection mediaDirection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RtcSessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", length = 20)
    private ConnectionType connectionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 30)
    private RtcEndReason endReason;

    @Column(name = "verification_memo", length = 1000)
    private String verificationMemo;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @Column(name = "inspection_submitted_at")
    private LocalDateTime inspectionSubmittedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    protected RtcSession() {}

    public static RtcSession waiting(
            Long appointmentId,
            Long chatRoomId,
            Long listingId,
            Long sellerId,
            Long buyerId,
            LocalDateTime expiresAt) {
        RtcSession session = new RtcSession();
        session.sessionKey = UUID.randomUUID();
        session.callAppointmentId = appointmentId;
        session.chatRoomId = chatRoomId;
        session.listingId = listingId;
        session.sellerId = sellerId;
        session.buyerId = buyerId;
        session.mediaDirection = MediaDirection.SELLER_TO_BUYER;
        session.status = RtcSessionStatus.WAITING;
        session.expiresAt = expiresAt;
        return session;
    }

    public void connect(ConnectionType type) {
        if (status == RtcSessionStatus.ENDED || status == RtcSessionStatus.EXPIRED) {
            throw new IllegalStateException("session already closed");
        }
        status = RtcSessionStatus.CONNECTED;
        connectionType = type;
        if (connectedAt == null) connectedAt = LocalDateTime.now();
    }

    public void end(RtcEndReason reason, String memo) {
        if (status == RtcSessionStatus.ENDED || status == RtcSessionStatus.EXPIRED) return;
        status = RtcSessionStatus.ENDED;
        endReason = reason;
        verificationMemo = memo;
        endedAt = LocalDateTime.now();
    }

    public void disconnectAfterInspection(
            RtcEndReason reason, String memo, LocalDateTime submittedAt) {
        if (status == RtcSessionStatus.ENDED || status == RtcSessionStatus.EXPIRED) return;
        status = RtcSessionStatus.WAITING;
        endReason = reason;
        verificationMemo = memo;
        if (inspectionSubmittedAt == null) inspectionSubmittedAt = submittedAt;
    }

    public boolean expireIfDue(LocalDateTime now) {
        if (status == RtcSessionStatus.ENDED || status == RtcSessionStatus.EXPIRED) return false;
        if (expiresAt != null && expiresAt.isAfter(now)) {
            return false;
        }
        status = RtcSessionStatus.EXPIRED;
        endReason = RtcEndReason.TIMEOUT;
        endedAt = now;
        return true;
    }

    public boolean isClosed() {
        return status == RtcSessionStatus.ENDED || status == RtcSessionStatus.EXPIRED;
    }

    public boolean isParticipant(Long memberId) {
        return sellerId.equals(memberId) || buyerId.equals(memberId);
    }

    public Long getId() {
        return id;
    }

    public Long getCallAppointmentId() {
        return callAppointmentId;
    }

    public Long getChatRoomId() {
        return chatRoomId;
    }

    public Long getListingId() {
        return listingId;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public Long getBuyerId() {
        return buyerId;
    }

    public RtcSessionStatus getStatus() {
        return status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getConnectedAt() {
        return connectedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public LocalDateTime getInspectionSubmittedAt() {
        return inspectionSubmittedAt;
    }

    public String getVerificationMemo() {
        return verificationMemo;
    }
}
