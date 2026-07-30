package com.c203.limit.domain.product.service;

import com.c203.limit.domain.product.entity.MediaUploadSession;
import com.c203.limit.domain.product.entity.MediaUploadStatus;
import com.c203.limit.domain.product.repository.MediaUploadSessionRepository;
import com.c203.limit.domain.product.storage.MediaObjectStorage;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class MediaUploadCleanupScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(MediaUploadCleanupScheduler.class);

    private final MediaUploadSessionRepository uploadSessionRepository;
    private final MediaObjectStorage storage;

    public MediaUploadCleanupScheduler(
            MediaUploadSessionRepository uploadSessionRepository,
            MediaObjectStorage storage) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.storage = storage;
    }

    @Scheduled(
            fixedDelayString = "${limit.storage.s3.cleanup-delay-ms:3600000}",
            initialDelayString = "${limit.storage.s3.cleanup-initial-delay-ms:60000}")
    public void cleanupExpiredUploads() {
        List<MediaUploadSession> sessions =
                uploadSessionRepository.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        MediaUploadStatus.PENDING, LocalDateTime.now());
        for (MediaUploadSession session : sessions) {
            cleanup(session);
        }
        if (!sessions.isEmpty()) {
            log.info("expired media upload cleanup completed: count={}", sessions.size());
        }
    }

    private void cleanup(MediaUploadSession session) {
        try {
            storage.delete(session.getBucketName(), session.getObjectKey());
        } catch (S3Exception exception) {
            log.warn(
                    "expired media object cleanup deferred: uploadId={}, status={}",
                    session.getUploadId(),
                    exception.statusCode());
            return;
        }
        session.expire();
        uploadSessionRepository.save(session);
    }
}
