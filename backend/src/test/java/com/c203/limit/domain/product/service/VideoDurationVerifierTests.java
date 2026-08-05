package com.c203.limit.domain.product.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.product.entity.MediaUploadSession;
import com.c203.limit.domain.product.storage.MediaObjectStorage;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * verify()는 실제로 ffprobe 프로세스를 실행하므로(외부 바이너리 의존) 단위 테스트로 안전하게 검증할
 * 수 있는 범위는 "언제 아예 실행하지 않고 조용히 반환하는가"의 두 가지 스킵 분기뿐이다.
 */
class VideoDurationVerifierTests {

    private final MediaObjectStorage storage = mock(MediaObjectStorage.class);

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
}
