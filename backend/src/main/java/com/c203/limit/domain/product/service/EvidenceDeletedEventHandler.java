package com.c203.limit.domain.product.service;

import com.c203.limit.domain.product.storage.MediaObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EvidenceDeletedEventHandler {

    private static final Logger log =
            LoggerFactory.getLogger(EvidenceDeletedEventHandler.class);
    private final MediaObjectStorage storage;

    public EvidenceDeletedEventHandler(MediaObjectStorage storage) {
        this.storage = storage;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deleteObject(EvidenceDeletedEvent event) {
        try {
            storage.delete(event.bucket(), event.objectKey());
        } catch (RuntimeException exception) {
            log.error(
                    "evidence S3 delete failed after DB commit: objectKey={}",
                    event.objectKey(),
                    exception);
        }
    }
}
