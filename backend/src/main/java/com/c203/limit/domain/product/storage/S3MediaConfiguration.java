package com.c203.limit.domain.product.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3MediaProperties.class)
public class S3MediaConfiguration {

    @Bean
    S3Client mediaS3Client(S3MediaProperties properties) {
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyleAccess())
                .build();
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .serviceConfiguration(serviceConfiguration);
        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint());
        }
        return builder.build();
    }

    @Bean
    S3Presigner mediaS3Presigner(S3MediaProperties properties) {
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyleAccess())
                .build();
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .serviceConfiguration(serviceConfiguration);
        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint());
        }
        return builder.build();
    }
}
