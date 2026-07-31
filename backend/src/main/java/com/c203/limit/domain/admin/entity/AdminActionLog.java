package com.c203.limit.domain.admin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;

    protected AdminActionLog() {}

    public static AdminActionLog of(
            Long admin, String action, String target, Long targetId, String reason) {
        var l = new AdminActionLog();
        l.adminId = admin;
        l.actionType = action;
        l.targetType = target;
        l.targetId = targetId;
        l.reason = reason;
        l.createdAt = LocalDateTime.now();
        return l;
    }

    public static AdminActionLog revision(
            Long adminId, Long actionLogId, String beforeReason, String afterReason) {
        AdminActionLog log =
                of(
                        adminId,
                        "ADMIN_ACTION_LOG_UPDATE",
                        "ADMIN_ACTION_LOG",
                        actionLogId,
                        "관리자 작업 로그 사유 수정");
        log.beforeData = reasonData(beforeReason);
        log.afterData = reasonData(afterReason);
        return log;
    }

    public void updateReason(String reason) {
        this.reason = trimToNull(reason);
    }

    private static String reasonData(String reason) {
        if (reason == null) {
            return "{\"reason\":null}";
        }
        String escaped =
                reason.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\r", "\\r")
                        .replace("\n", "\\n")
                        .replace("\t", "\\t");
        return "{\"reason\":\"" + escaped + "\"}";
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
