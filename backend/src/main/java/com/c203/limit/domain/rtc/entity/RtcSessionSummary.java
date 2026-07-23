package com.c203.limit.domain.rtc.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rtc_session_summary")
public class RtcSessionSummary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rtc_session_id", nullable = false, unique = true)
    private Long rtcSessionId;

    @Column(name = "connection_setup_ms")
    private Long connectionSetupMs;

    @Column(name = "used_turn", nullable = false)
    private boolean isUsedTurn;

    @Column(name = "avg_rtt_ms", precision = 12, scale = 3)
    private BigDecimal averageRttMs;

    @Column(name = "max_rtt_ms", precision = 12, scale = 3)
    private BigDecimal maximumRttMs;

    @Column(name = "avg_packet_loss_rate", precision = 8, scale = 5)
    private BigDecimal averagePacketLossRate;

    @Column(name = "avg_jitter_ms", precision = 12, scale = 3)
    private BigDecimal averageJitterMs;

    @Column(name = "avg_inbound_bitrate_kbps", precision = 14, scale = 3)
    private BigDecimal averageInboundBitrateKbps;

    @Column(name = "avg_fps", precision = 8, scale = 3)
    private BigDecimal averageFps;

    @Column(name = "ice_restart_count", nullable = false)
    private Integer iceRestartCount;

    @Column(name = "reconnect_count", nullable = false)
    private Integer reconnectCount;

    @Column(name = "recovery_succeeded", nullable = false)
    private boolean isRecoverySucceeded;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    protected RtcSessionSummary() {}
}
