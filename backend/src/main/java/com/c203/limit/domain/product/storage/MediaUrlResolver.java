package com.c203.limit.domain.product.storage;

import org.springframework.stereotype.Component;

@Component
public class MediaUrlResolver {

    private final MediaObjectStorage storage;
    private final S3MediaProperties properties;

    public MediaUrlResolver(MediaObjectStorage storage, S3MediaProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    public String resolve(String objectKey, String legacyCdnUrl) {
        if (objectKey == null || objectKey.isBlank()) {
            return legacyCdnUrl;
        }
        if (properties.publicBaseUrl() != null && !properties.publicBaseUrl().isBlank()) {
            return properties.publicBaseUrl().replaceAll("/$", "") + "/" + objectKey;
        }
        if (properties.bucket() == null || properties.bucket().isBlank()) {
            return legacyCdnUrl;
        }
        return storage
                .presignGet(properties.bucket(), objectKey, properties.downloadTtl())
                .toString();
    }
}
