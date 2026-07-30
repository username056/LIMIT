package com.c203.limit.domain.product.service;

import com.c203.limit.domain.product.entity.MediaUploadSession;
import com.c203.limit.domain.product.storage.MediaObjectStorage;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VideoDurationVerifier {
    private static final Logger log = LoggerFactory.getLogger(VideoDurationVerifier.class);

    private final MediaObjectStorage storage;
    private final boolean enabled;
    private final String executable;
    private final Duration timeout;

    public VideoDurationVerifier(
            MediaObjectStorage storage,
            @Value("${limit.storage.s3.ffprobe-enabled:false}") boolean enabled,
            @Value("${limit.storage.s3.ffprobe-executable:ffprobe}") String executable,
            @Value("${limit.storage.s3.ffprobe-timeout:15s}") Duration timeout) {
        this.storage = storage;
        this.enabled = enabled;
        this.executable = executable;
        this.timeout = timeout;
    }

    public void verify(MediaUploadSession session) {
        if (!enabled || session.getExpectedDurationSeconds() == null) return;
        String sourceUrl = storage.presignGet(
                        session.getBucketName(), session.getObjectKey(), Duration.ofMinutes(2))
                .toString();
        Process process = null;
        try {
            process = new ProcessBuilder(
                            executable, "-v", "error", "-show_entries", "format=duration",
                            "-of", "default=noprint_wrappers=1:nokey=1", sourceUrl)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                log.warn(
                        "Video duration verification timed out: uploadId={}",
                        session.getUploadId());
                throw new BusinessException(ErrorCode.MEDIA_STORAGE_UNAVAILABLE);
            }
            String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0 || output.isBlank()) {
                log.warn(
                        "Video duration metadata could not be read: uploadId={}",
                        session.getUploadId());
                throw new BusinessException(ErrorCode.MEDIA_UPLOAD_MISMATCH);
            }
            double actualSeconds = Double.parseDouble(output.lines().findFirst().orElseThrow());
            if (Math.abs(actualSeconds - session.getExpectedDurationSeconds()) > 1.5d) {
                log.warn(
                        "Video duration mismatch: uploadId={}, expectedSeconds={}, actualSeconds={}",
                        session.getUploadId(),
                        session.getExpectedDurationSeconds(),
                        actualSeconds);
                throw new BusinessException(ErrorCode.MEDIA_UPLOAD_MISMATCH);
            }
            log.info(
                    "Video duration verified: uploadId={}, durationSeconds={}",
                    session.getUploadId(),
                    actualSeconds);
        } catch (IOException | InterruptedException | NumberFormatException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error(
                    "Video duration verification failed: uploadId={}, failureType={}",
                    session.getUploadId(),
                    exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_UNAVAILABLE);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }
}
