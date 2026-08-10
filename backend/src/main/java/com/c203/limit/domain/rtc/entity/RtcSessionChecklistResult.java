package com.c203.limit.domain.rtc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
        name = "rtc_session_checklist_result",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_rtc_checklist_session_item",
                        columnNames = {"rtc_session_id", "listing_checklist_item_id"}))
public class RtcSessionChecklistResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rtc_session_id", nullable = false)
    private Long rtcSessionId;

    @Column(name = "listing_checklist_item_id", nullable = false)
    private Long listingChecklistItemId;

    @Column(name = "checked_by", nullable = false)
    private Long checkedBy;

    @Column(name = "is_confirmed", nullable = false)
    private boolean isConfirmed;

    @Column(length = 500)
    private String note;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    protected RtcSessionChecklistResult() {}

    public static RtcSessionChecklistResult record(
            Long sessionId, Long checklistItemId, Long checkedBy, boolean confirmed, String note) {
        RtcSessionChecklistResult result = new RtcSessionChecklistResult();
        result.rtcSessionId = sessionId;
        result.listingChecklistItemId = checklistItemId;
        result.checkedBy = checkedBy;
        result.isConfirmed = confirmed;
        result.note = note;
        result.checkedAt = LocalDateTime.now(ZoneOffset.UTC);
        return result;
    }

    public Long getListingChecklistItemId() {
        return listingChecklistItemId;
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }

    public String getNote() {
        return note;
    }
}
