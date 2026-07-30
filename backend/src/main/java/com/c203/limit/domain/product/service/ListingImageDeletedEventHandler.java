package com.c203.limit.domain.product.service;

import com.c203.limit.domain.product.storage.MediaObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ListingImageDeletedEventHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ListingImageDeletedEventHandler.class);
    private final MediaObjectStorage storage;

    public ListingImageDeletedEventHandler(MediaObjectStorage storage) {
        this.storage = storage;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deleteObject(ListingImageDeletedEvent event) {
        try {
            storage.delete(event.bucket(), event.objectKey());
        } catch (RuntimeException exception) {
            log.error(
                    "listing image S3 delete failed after DB commit: objectKey={}",
                    event.objectKey(),
                    exception);
        }
    }
}
