package com.c203.limit.domain.product.entity;

import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "media_upload_session",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_media_upload_session_object",
                        columnNames = {"bucket_name", "object_key"}))
public class MediaUploadSession {

    @Id
    @Column(name = "upload_id", length = 36)
    private String uploadId;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_checklist_item_id")
    private ListingChecklistItem checklistItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MediaUploadPurpose purpose;

    @Column(name = "bucket_name", nullable = false, length = 255)
    private String bucketName;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "final_object_key", nullable = false, length = 500)
    private String finalObjectKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "expected_mime_type", nullable = false, length = 100)
    private String expectedMimeType;

    @Column(name = "expected_file_size", nullable = false)
    private long expectedFileSize;

    @Column(name = "expected_duration_seconds")
    private Integer expectedDurationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MediaUploadStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static MediaUploadSession evidence(
            String uploadId,
            Long uploaderId,
            Long listingId,
            ListingChecklistItem checklistItem,
            String bucketName,
            String objectKey,
            String finalObjectKey,
            String originalFilename,
            String expectedMimeType,
            long expectedFileSize,
            Integer expectedDurationSeconds,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        MediaUploadSession session = new MediaUploadSession();
        session.uploadId = uploadId;
        session.uploaderId = uploaderId;
        session.listingId = listingId;
        session.checklistItem = checklistItem;
        session.purpose = MediaUploadPurpose.EVIDENCE;
        session.bucketName = bucketName;
        session.objectKey = objectKey;
        session.finalObjectKey = finalObjectKey;
        session.originalFilename = originalFilename;
        session.expectedMimeType = expectedMimeType;
        session.expectedFileSize = expectedFileSize;
        session.expectedDurationSeconds = expectedDurationSeconds;
        session.status = MediaUploadStatus.PENDING;
        session.expiresAt = expiresAt;
        session.createdAt = createdAt;
        return session;
    }

    public static MediaUploadSession listingImage(
            String uploadId,
            Long uploaderId,
            Long listingId,
            String bucketName,
            String objectKey,
            String finalObjectKey,
            String originalFilename,
            String expectedMimeType,
            long expectedFileSize,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        MediaUploadSession session = new MediaUploadSession();
        session.uploadId = uploadId;
        session.uploaderId = uploaderId;
        session.listingId = listingId;
        session.purpose = MediaUploadPurpose.LISTING_IMAGE;
        session.bucketName = bucketName;
        session.objectKey = objectKey;
        session.finalObjectKey = finalObjectKey;
        session.originalFilename = originalFilename;
        session.expectedMimeType = expectedMimeType;
        session.expectedFileSize = expectedFileSize;
        session.status = MediaUploadStatus.PENDING;
        session.expiresAt = expiresAt;
        session.createdAt = createdAt;
        return session;
    }

    public void complete(LocalDateTime completedAt) {
        status = MediaUploadStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public void expire() {
        if (status == MediaUploadStatus.PENDING) {
            status = MediaUploadStatus.EXPIRED;
        }
    }
}
