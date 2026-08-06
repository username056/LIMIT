package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.product.entity.MediaUploadSession;
import com.c203.limit.domain.product.entity.MediaUploadStatus;
import com.c203.limit.domain.product.repository.MediaUploadSessionRepository;
import com.c203.limit.domain.product.storage.MediaObjectStorage;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class MediaUploadCleanupSchedulerTests {

    private static final String BUCKET = "limit-media-test";

    @Mock MediaUploadSessionRepository uploadSessionRepository;
    @Mock MediaObjectStorage storage;

    @Test
    void deletesExpiredObjectsAndMarksSessionsExpired() {
        MediaUploadSession first = session("upload-1", "tmp/1.png");
        MediaUploadSession second = session("upload-2", "tmp/2.png");
        expiredSessions(List.of(first, second));

        scheduler().cleanupExpiredUploads();

        verify(storage).delete(BUCKET, "tmp/1.png");
        verify(storage).delete(BUCKET, "tmp/2.png");
        verify(uploadSessionRepository).save(first);
        verify(uploadSessionRepository).save(second);
        assertThat(first.getStatus()).isEqualTo(MediaUploadStatus.EXPIRED);
        assertThat(second.getStatus()).isEqualTo(MediaUploadStatus.EXPIRED);
    }

    @Test
    void keepsSessionPendingWhenObjectDeletionFails() {
        MediaUploadSession session = session("upload-1", "tmp/1.png");
        expiredSessions(List.of(session));
        doThrow(S3Exception.builder().statusCode(503).message("slow down").build())
                .when(storage)
                .delete(BUCKET, "tmp/1.png");

        scheduler().cleanupExpiredUploads();

        verify(uploadSessionRepository, never()).save(any(MediaUploadSession.class));
        assertThat(session.getStatus()).isEqualTo(MediaUploadStatus.PENDING);
    }

    @Test
    void continuesCleanupForRemainingSessionsAfterOneFailure() {
        MediaUploadSession failing = session("upload-1", "tmp/1.png");
        MediaUploadSession succeeding = session("upload-2", "tmp/2.png");
        expiredSessions(List.of(failing, succeeding));
        doThrow(S3Exception.builder().statusCode(500).message("boom").build())
                .when(storage)
                .delete(BUCKET, "tmp/1.png");

        scheduler().cleanupExpiredUploads();

        verify(storage).delete(BUCKET, "tmp/2.png");
        verify(uploadSessionRepository).save(succeeding);
        verify(uploadSessionRepository, never()).save(failing);
        assertThat(failing.getStatus()).isEqualTo(MediaUploadStatus.PENDING);
        assertThat(succeeding.getStatus()).isEqualTo(MediaUploadStatus.EXPIRED);
    }

    @Test
    void doesNothingWhenNoExpiredSessionExists() {
        expiredSessions(List.of());

        scheduler().cleanupExpiredUploads();

        verifyNoInteractions(storage);
        verify(uploadSessionRepository, never()).save(any(MediaUploadSession.class));
    }

    private MediaUploadCleanupScheduler scheduler() {
        return new MediaUploadCleanupScheduler(uploadSessionRepository, storage);
    }

    private void expiredSessions(List<MediaUploadSession> sessions) {
        when(uploadSessionRepository.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        eq(MediaUploadStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(sessions);
    }

    private MediaUploadSession session(String uploadId, String objectKey) {
        return MediaUploadSession.listingImage(
                uploadId,
                1L,
                2L,
                BUCKET,
                objectKey,
                "listing/2/" + uploadId + ".png",
                "photo.png",
                "image/png",
                2048L,
                LocalDateTime.of(2026, 8, 6, 0, 0),
                LocalDateTime.of(2026, 8, 5, 23, 0));
    }
}
