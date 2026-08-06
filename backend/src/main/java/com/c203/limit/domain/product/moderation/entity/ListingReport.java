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
@Table(name = "listing_report")
public class ListingReport extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ListingReportCategory category;

    @Column(nullable = false, length = 1000)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ListingReportStatus status;

    @Column(name = "reviewer_admin_id")
    private Long reviewerAdminId;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    public static ListingReport create(Long listingId, Long reporterId, ListingReportCategory category, String detail) {
        ListingReport report = new ListingReport();
        report.listingId = listingId;
        report.reporterId = reporterId;
        report.category = category;
        report.detail = detail.trim();
        report.status = ListingReportStatus.PENDING;
        return report;
    }

    public void decide(ModerationDecision decision, Long adminId, String note, LocalDateTime now) {
        if (status != ListingReportStatus.PENDING) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_REVIEWED);
        }
        status = switch (decision) {
            case DISMISS -> ListingReportStatus.DISMISSED;
            case WARN -> ListingReportStatus.WARNING_ISSUED;
            case SUSPEND -> ListingReportStatus.SUSPENDED;
        };
        reviewerAdminId = adminId;
        adminNote = trimToNull(note);
        reviewedAt = now;
    }

    public void acknowledge(LocalDateTime now) {
        if (status != ListingReportStatus.WARNING_ISSUED) {
            throw new BusinessException(ErrorCode.MODERATION_STATE_CONFLICT);
        }
        status = ListingReportStatus.RESOLVED;
        acknowledgedAt = now;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
