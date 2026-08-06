package com.c203.limit.domain.product.storage;

import java.net.URL;
import java.time.Duration;

public interface MediaObjectStorage {

    URL presignPut(
            String bucket, String objectKey, String contentType, long contentLength, Duration ttl);

    URL presignGet(String bucket, String objectKey, Duration ttl);

    StoredObject head(String bucket, String objectKey);

    byte[] read(String bucket, String objectKey, long maxBytes);

    void promote(String bucket, String sourceKey, String destinationKey, String contentType);

    void delete(String bucket, String objectKey);

    record StoredObject(long contentLength, String contentType) {}
}
