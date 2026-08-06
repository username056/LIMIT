package com.c203.limit.domain.product.moderation.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.product.storage.MediaObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListingImageHashEventHandlerTests {
    @Mock MediaObjectStorage storage;
    @Mock ListingImageHashCalculator calculator;
    @Mock ListingImageHashPersistenceService persistenceService;

    ListingImageHashEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ListingImageHashEventHandler(storage, calculator, persistenceService);
    }

    @Test
    void completedImageIsReadHashedAndPersisted() {
        byte[] content = new byte[] {1, 2, 3};
        var hashes = new ListingImageHashCalculator.ImageHashes("sha", "0000000000000000");
        when(storage.read("bucket", "products/101", 15L * 1024L * 1024L))
                .thenReturn(content);
        when(calculator.calculate(content)).thenReturn(hashes);

        handler.analyze(new ListingImageCompletedEvent(101L, "bucket", "products/101"));

        verify(persistenceService).record(101L, hashes);
    }

    @Test
    void storageFailureDoesNotBreakCommittedUploadFlow() {
        when(storage.read(eq("bucket"), eq("products/101"), eq(15L * 1024L * 1024L)))
                .thenThrow(new IllegalStateException("storage unavailable"));

        handler.analyze(new ListingImageCompletedEvent(101L, "bucket", "products/101"));

        verify(calculator, never()).calculate(org.mockito.ArgumentMatchers.any());
        verify(persistenceService, never())
                .record(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
