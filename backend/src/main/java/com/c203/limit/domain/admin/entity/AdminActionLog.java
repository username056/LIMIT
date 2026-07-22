package com.c203.limit.domain.admin.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "admin_action_log")
public class AdminActionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(length = 500)
    private String reason;

    @Column(name = "before_data", columnDefinition = "json")
    private String beforeData;

    @Column(name = "after_data", columnDefinition = "json")
    private String afterData;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AdminActionLog() {}

    public static AdminActionLog of(
            Long admin, String action, String target, Long targetId, String reason) {
        var l = new AdminActionLog();
        l.adminId = admin;
        l.actionType = action;
        l.targetType = target;
        l.targetId = targetId;
        l.reason = reason;
        l.createdAt = OffsetDateTime.now();
        return l;
    }

    public Long getId() {
        return id;
    }

    public Long getAdminId() {
        return adminId;
    }

    public String getActionType() {
        return actionType;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getReason() {
        return reason;
    }

    public String getBeforeData() {
        return beforeData;
    }

    public String getAfterData() {
        return afterData;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
