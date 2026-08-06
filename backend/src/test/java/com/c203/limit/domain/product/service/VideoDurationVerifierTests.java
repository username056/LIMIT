package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.product.entity.MediaUploadSession;
import com.c203.limit.domain.product.storage.MediaObjectStorage;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * verify()는 ffprobe 프로세스를 실제로 실행한다. 외부 바이너리에 의존하지 않도록 임시 디렉터리에
 * 만든 스텁 스크립트를 executable로 주입해 출력·종료 코드별 분기를 결정적으로 검증한다.
 */
class VideoDurationVerifierTests {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    private final MediaObjectStorage storage = mock(MediaObjectStorage.class);

    @TempDir Path tempDir;

    @Test
    void doesNothingWhenFfprobeIsDisabled() {
        VideoDurationVerifier verifier =
                new VideoDurationVerifier(storage, false, "ffprobe", Duration.ofSeconds(15));
        MediaUploadSession session = mock(MediaUploadSession.class);

        verifier.verify(session);

        // enabled=false이면 session.getExpectedDurationSeconds()조차 평가하지 않고(단락 평가) 곧바로
        // 반환하므로 storage와도, session과도 전혀 상호작용이 없어야 한다.
        verifyNoInteractions(storage);
        verifyNoInteractions(session);
    }

    @Test
    void doesNothingWhenSessionHasNoExpectedDuration() {
        VideoDurationVerifier verifier =
                new VideoDurationVerifier(storage, true, "ffprobe", Duration.ofSeconds(15));
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getExpectedDurationSeconds()).thenReturn(null);

        verifier.verify(session);

        verifyNoInteractions(storage);
    }

    @Test
    void acceptsMeasuredDurationWithinTolerance() throws Exception {
        MediaUploadSession session = session(10);
        VideoDurationVerifier verifier = verifierUsing(scriptPrinting("10.4"), Duration.ofSeconds(15));

        assertThatCode(() -> verifier.verify(session)).doesNotThrowAnyException();

        verify(storage)
                .presignGet("limit-dev-media", "tmp/55/upload-1.mp4", Duration.ofMinutes(2));
    }

    @Test
    void rejectsMeasuredDurationOutsideTolerance() throws Exception {
        MediaUploadSession session = session(10);
        VideoDurationVerifier verifier = verifierUsing(scriptPrinting("30.0"), Duration.ofSeconds(15));

        assertThatThrownBy(() -> verifier.verify(session))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_MISMATCH));
    }

    @Test
    void rejectsUploadWhenProbeReportsNoDurationMetadata() throws Exception {
        MediaUploadSession session = session(10);
        VideoDurationVerifier verifier = verifierUsing(scriptPrinting(null), Duration.ofSeconds(15));

        assertThatThrownBy(() -> verifier.verify(session))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_MISMATCH));
    }

    @Test
    void mapsUnparsableDurationOutputToStorageUnavailable() throws Exception {
        MediaUploadSession session = session(10);
        VideoDurationVerifier verifier =
                verifierUsing(scriptPrinting("not-a-number"), Duration.ofSeconds(15));

        assertThatThrownBy(() -> verifier.verify(session))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_STORAGE_UNAVAILABLE));
    }

    @Test
    void mapsProbeExecutionFailureToStorageUnavailable() {
        MediaUploadSession session = session(10);
        VideoDurationVerifier verifier =
                verifierUsing(
                        tempDir.resolve("missing-ffprobe").toAbsolutePath().toString(),
                        Duration.ofSeconds(15));

        assertThatThrownBy(() -> verifier.verify(session))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_STORAGE_UNAVAILABLE));
    }

    @Test
    void mapsProbeTimeoutToStorageUnavailable() throws Exception {
        MediaUploadSession session = session(10);
        VideoDurationVerifier verifier =
                verifierUsing(sleepingScript(), Duration.ofMillis(200));

        assertThatThrownBy(() -> verifier.verify(session))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_STORAGE_UNAVAILABLE));
    }

    private VideoDurationVerifier verifierUsing(String executable, Duration timeout) {
        return new VideoDurationVerifier(storage, true, executable, timeout);
    }

    private MediaUploadSession session(Integer expectedDurationSeconds) {
        MediaUploadSession session = mock(MediaUploadSession.class);
        when(session.getExpectedDurationSeconds()).thenReturn(expectedDurationSeconds);
        when(session.getBucketName()).thenReturn("limit-dev-media");
        when(session.getObjectKey()).thenReturn("tmp/55/upload-1.mp4");
        try {
            when(storage.presignGet("limit-dev-media", "tmp/55/upload-1.mp4", Duration.ofMinutes(2)))
                    .thenReturn(URI.create("https://s3.example.test/upload-1.mp4").toURL());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return session;
    }

    /** 인자를 무시하고 지정한 값을 표준 출력으로 내보내는 스텁 실행 파일을 만든다. null이면 아무것도 출력하지 않는다. */
    private String scriptPrinting(String value) throws IOException {
        String body = value == null ? "" : "echo " + value;
        return writeScript("probe", body);
    }

    /** 타임아웃 분기를 재현하려고 충분히 오래 살아 있는 스텁 실행 파일을 만든다. */
    private String sleepingScript() throws IOException {
        return writeScript("sleeper", WINDOWS ? "ping -n 30 127.0.0.1 > nul" : "sleep 30");
    }

    private String writeScript(String name, String body) throws IOException {
        Path script = tempDir.resolve(WINDOWS ? name + ".bat" : name + ".sh");
        if (WINDOWS) {
            Files.writeString(script, "@echo off\r\n" + body + "\r\n");
        } else {
            Files.writeString(script, "#!/bin/sh\n" + body + "\n");
            if (!script.toFile().setExecutable(true)) {
                throw new IOException("stub script could not be marked executable");
            }
        }
        return script.toAbsolutePath().toString();
    }
}
