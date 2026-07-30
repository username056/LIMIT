package com.c203.limit.domain.product.storage;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("limit.storage.s3")
public record S3MediaProperties(
        String region,
        String bucket,
        URI endpoint,
        boolean pathStyleAccess,
        Duration uploadTtl,
        Duration downloadTtl,
        String publicBaseUrl) {}
