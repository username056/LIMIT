package com.c203.limit.domain.inspection.entity;

import com.c203.limit.domain.inspection.enums.EvidenceProcessingStatus;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 판매자가 업로드한 검수 증거(사진·영상·진단파일). DDL: evidence */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "evidence")
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_checklist_item_id")
    private ListingChecklistItem listingChecklistItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 30)
    private EvidenceType evidenceType;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "cdn_url", length = 500)
    private String cdnUrl;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 30)
    private EvidenceProcessingStatus processingStatus;

    public static Evidence upload(
            Long listingId,
            ListingChecklistItem listingChecklistItem,
            EvidenceType evidenceType,
            String s3Key,
            String mimeType,
            LocalDateTime capturedAt) {
        Evidence evidence = new Evidence();
        evidence.listingId = listingId;
        evidence.listingChecklistItem = listingChecklistItem;
        evidence.evidenceType = evidenceType;
        evidence.s3Key = s3Key;
        evidence.mimeType = mimeType;
        evidence.capturedAt = capturedAt;
        evidence.uploadedAt = LocalDateTime.now();
        evidence.processingStatus = EvidenceProcessingStatus.PENDING;
        return evidence;
    }

    public void markReady(String cdnUrl) {
        this.cdnUrl = cdnUrl;
        this.processingStatus = EvidenceProcessingStatus.READY;
    }

    public void markFailed() {
        this.processingStatus = EvidenceProcessingStatus.FAILED;
    }

    public void markInfected() {
        this.processingStatus = EvidenceProcessingStatus.INFECTED;
    }
}
