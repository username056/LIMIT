package com.c203.limit.domain.product.moderation.entity;

import com.c203.limit.global.common.BaseTimeEntity;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "moderation_risk_signal")
public class ModerationRiskSignal extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "risk_signal_id")
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "listing_id")
    private Long listingId;

    @Column(name = "related_listing_id")
    private Long relatedListingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 40)
    private ModerationRiskType type;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, length = 500)
    private String detail;

    @Column(nullable = false, unique = true, length = 180)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationRiskStatus status;

    @Column(name = "resolved_by_admin_id")
    private Long resolvedByAdminId;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public static ModerationRiskSignal create(
            Long sellerId,
            Long listingId,
            Long relatedListingId,
            ModerationRiskType type,
            int score,
            String detail,
            String fingerprint) {
        ModerationRiskSignal signal = new ModerationRiskSignal();
        signal.sellerId = sellerId;
        signal.listingId = listingId;
        signal.relatedListingId = relatedListingId;
        signal.type = type;
        signal.score = normalizedScore(score);
        signal.detail = detail;
        signal.fingerprint = fingerprint;
        signal.status = ModerationRiskStatus.OPEN;
        return signal;
    }

    public void refresh(int newScore, String newDetail) {
        if (status == ModerationRiskStatus.OPEN) {
            score = Math.max(score, normalizedScore(newScore));
            detail = newDetail;
        }
    }

    public void resolve(Long adminId, String note, LocalDateTime now) {
        if (status != ModerationRiskStatus.OPEN) {
            throw new BusinessException(ErrorCode.MODERATION_STATE_CONFLICT);
        }
        status = ModerationRiskStatus.RESOLVED;
        resolvedByAdminId = adminId;
        resolutionNote = note == null || note.isBlank() ? null : note.trim();
        resolvedAt = now;
    }

    private static int normalizedScore(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
