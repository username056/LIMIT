package com.c203.limit.domain.product.moderation.service;

import com.c203.limit.domain.product.storage.MediaObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ListingImageHashEventHandler {
    private static final Logger log = LoggerFactory.getLogger(ListingImageHashEventHandler.class);
    private static final long MAX_ANALYSIS_BYTES = 15L * 1024L * 1024L;

    private final MediaObjectStorage storage;
    private final ListingImageHashCalculator calculator;
    private final ListingImageHashPersistenceService persistenceService;

    public ListingImageHashEventHandler(
            MediaObjectStorage storage,
            ListingImageHashCalculator calculator,
            ListingImageHashPersistenceService persistenceService) {
        this.storage = storage;
        this.calculator = calculator;
        this.persistenceService = persistenceService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void analyze(ListingImageCompletedEvent event) {
        try {
            byte[] content = storage.read(event.bucket(), event.objectKey(), MAX_ANALYSIS_BYTES);
            persistenceService.record(event.imageId(), calculator.calculate(content));
        } catch (RuntimeException exception) {
            log.error(
                    "listing image risk analysis failed: imageId={}, objectKey={}",
                    event.imageId(),
                    event.objectKey(),
                    exception);
        }
    }
}
