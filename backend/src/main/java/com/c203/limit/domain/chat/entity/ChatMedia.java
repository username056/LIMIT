package com.c203.limit.domain.chat.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.c203.limit.domain.chat.domain.MediaType;
import com.c203.limit.domain.chat.domain.UploadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "chat_media", uniqueConstraints = {
        @UniqueConstraint(name = "UK_CHAT_MEDIA_KEY", columnNames = "media_key"),
        @UniqueConstraint(name = "UK_CHAT_MEDIA_OBJECT", columnNames = {"bucket_name", "object_key"})
})
public class ChatMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_key", nullable = false)
    private UUID mediaKey;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private MediaType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 20)
    private UploadStatus uploadStatus;

    @Column(name = "bucket_name", nullable = false, length = 255)
    private String bucketName;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "width_px")
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "upload_expires_at", nullable = false)
    private LocalDateTime uploadExpiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    protected ChatMedia() {}

    public static ChatMedia verified(
            UUID mediaKey,
            Long chatRoomId,
            Long uploaderId,
            MediaType type,
            String bucketName,
            String objectKey,
            String originalFilename,
            String mimeType,
            long fileSizeBytes) {
        ChatMedia media = new ChatMedia();
        media.mediaKey = mediaKey;
        media.chatRoomId = chatRoomId;
        media.uploaderId = uploaderId;
        media.type = type;
        media.uploadStatus = UploadStatus.VERIFIED;
        media.bucketName = bucketName;
        media.objectKey = objectKey;
        media.originalFilename = originalFilename;
        media.mimeType = mimeType;
        media.fileSizeBytes = fileSizeBytes;
        media.uploadExpiresAt = LocalDateTime.now();
        media.verifiedAt = LocalDateTime.now();
        return media;
    }

    public Long getId() { return id; }
    public Long getChatRoomId() { return chatRoomId; }
    public Long getUploaderId() { return uploaderId; }
    public MediaType getType() { return type; }
    public UploadStatus getUploadStatus() { return uploadStatus; }
    public String getBucketName() { return bucketName; }
    public String getObjectKey() { return objectKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMimeType() { return mimeType; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
}
