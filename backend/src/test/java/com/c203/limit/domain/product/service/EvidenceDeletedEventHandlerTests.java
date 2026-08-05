package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.c203.limit.domain.product.storage.MediaObjectStorage;
import org.junit.jupiter.api.Test;

/**
 * DB 커밋 후 S3 오브젝트 삭제를 시도하되, 스토리지 예외가 나도 커밋 자체는 이미 끝난 뒤이므로
 * 예외를 삼키고 로그만 남기는지(트랜잭션 이벤트 리스너가 상위로 예외를 전파하지 않는지) 확인한다.
 */
class EvidenceDeletedEventHandlerTests {

    private final MediaObjectStorage storage = mock(MediaObjectStorage.class);
    private final EvidenceDeletedEventHandler handler = new EvidenceDeletedEventHandler(storage);

    @Test
    void deletesTheObjectFromTheEventsBucketAndKey() {
        handler.deleteObject(new EvidenceDeletedEvent("limit-bucket", "evidence/1/final.jpg"));

        verify(storage).delete("limit-bucket", "evidence/1/final.jpg");
    }

    @Test
    void swallowsStorageExceptionsInsteadOfPropagatingThem() {
        doThrow(new RuntimeException("S3 unavailable"))
                .when(storage)
                .delete("limit-bucket", "evidence/1/final.jpg");

        assertThatCode(() -> handler.deleteObject(new EvidenceDeletedEvent("limit-bucket", "evidence/1/final.jpg")))
                .doesNotThrowAnyException();
    }
}
