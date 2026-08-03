package com.c203.limit.domain.inspection.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "inspection_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionSession {
    @Id
    @Column(name = "session_key", length = 36, nullable = false)
    private String sessionKey;

    @Column(name = "pairing_code_hash", columnDefinition = "BINARY(32)", nullable = false)
    private byte[] pairingCodeHash;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InspectionSessionStatus status;

    @Column(name = "agent_token_hash", columnDefinition = "BINARY(32)")
    private byte[] agentTokenHash;

    @Column(name = "collector_version", length = 30)
    private String collectorVersion;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paired_at")
    private LocalDateTime pairedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static InspectionSession create(
            String sessionKey,
            byte[] pairingCodeHash,
            Long sellerId,
            Long listingId,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        InspectionSession session = new InspectionSession();
        session.sessionKey = sessionKey;
        session.pairingCodeHash = pairingCodeHash;
        session.sellerId = sellerId;
        session.listingId = listingId;
        session.status = InspectionSessionStatus.CREATED;
        session.expiresAt = expiresAt;
        session.createdAt = createdAt;
        return session;
    }

    public void pair(byte[] tokenHash, String version, LocalDateTime pairedAt) {
        this.agentTokenHash = tokenHash;
        this.collectorVersion = version;
        this.pairedAt = pairedAt;
        this.status = InspectionSessionStatus.PAIRED;
    }

    public void markUploading() {
        this.status = InspectionSessionStatus.UPLOADING;
    }

    public void complete(LocalDateTime completedAt) {
        this.completedAt = completedAt;
        this.status = InspectionSessionStatus.COMPLETED;
    }

    public void expire() {
        this.status = InspectionSessionStatus.EXPIRED;
    }
}
