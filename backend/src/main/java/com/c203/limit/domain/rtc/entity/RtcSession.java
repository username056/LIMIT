package com.c203.limit.domain.rtc.entity;

import java.time.LocalDateTime;
import java.util.UUID;

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

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    protected RtcSession() {}
}
