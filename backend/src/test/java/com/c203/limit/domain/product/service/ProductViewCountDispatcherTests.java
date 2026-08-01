package com.c203.limit.domain.product.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class ProductViewCountDispatcherTests {

    @Mock ProductViewCountRecorder recorder;
    ProductViewCountDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new ProductViewCountDispatcher(recorder);
    }

    @Test
    void delegatesViewCountRecording() {
        dispatcher.dispatch(7L);

        verify(recorder).record(7L);
    }

    @Test
    void containsFailureAfterRecorderTransactionEnds() {
        when(recorder.record(7L)).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatCode(() -> dispatcher.dispatch(7L)).doesNotThrowAnyException();
    }
}
