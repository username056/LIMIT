package com.c203.limit.domain.admin.entity;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.global.exception.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "member_sanction")
public class MemberRestriction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sanction_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private Member member;

    @Column(name = "restriction_type", nullable = false)
    private String type;

    @Column(name = "reason_code", nullable = false)
    private String reasonCode;

    @Column(name = "reason_detail", nullable = false, length = 500)
    private String reasonDetail;

    @Column(nullable = false)
    private String status;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "admin_id", nullable = false)
    private Long createdBy;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "release_admin_id")
    private Long releasedBy;

    @Column(name = "release_reason", length = 500)
    private String releaseReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected MemberRestriction() {}

    public static MemberRestriction create(
            Member m,
            String type,
            String code,
            String detail,
            LocalDateTime start,
            LocalDateTime end,
            Long admin) {
        if (start == null || end == null || !end.isAfter(start))
            throw new BusinessException(ErrorCode.INVALID_RESTRICTION_PERIOD);
        var r = new MemberRestriction();
        r.member = m;
        r.type = type;
        r.reasonCode = code;
        r.reasonDetail = detail;
        r.status = "ACTIVE";
        r.startsAt = start;
        r.endsAt = end;
        r.createdBy = admin;
        r.createdAt = LocalDateTime.now();
        return r;
    }

    public void release(Long admin, String reason) {
        if (!"ACTIVE".equals(status)) throw new BusinessException(ErrorCode.RESTRICTION_NOT_ACTIVE);
        status = "RELEASED";
        releasedBy = admin;
        releaseReason = reason;
        releasedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public String getType() {
        return type;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReasonDetail() {
        return reasonDetail;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }

    public Long getReleasedBy() {
        return releasedBy;
    }

    public String getReleaseReason() {
        return releaseReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
