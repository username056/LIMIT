package com.c203.limit.domain.product.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * resolve()의 4단계 폴백을 확인한다: objectKey 없음→레거시 URL, publicBaseUrl 설정됨→직접 조립,
 * bucket 미설정→레거시 URL로 폴백, 그 외→presign 호출.
 */
class MediaUrlResolverTests {

    private final MediaObjectStorage storage = mock(MediaObjectStorage.class);

    private MediaUrlResolver resolver(String publicBaseUrl, String bucket) {
        S3MediaProperties properties = new S3MediaProperties(
                "ap-northeast-2", bucket, URI.create("https://s3.example.com"), false,
                Duration.ofMinutes(10), Duration.ofMinutes(10), publicBaseUrl);
        return new MediaUrlResolver(storage, properties);
    }

    @Test
    void returnsLegacyUrlWhenObjectKeyIsNull() {
        MediaUrlResolver resolver = resolver(null, "limit-bucket");

        String result = resolver.resolve(null, "https://legacy.example.com/old.jpg");

        assertThat(result).isEqualTo("https://legacy.example.com/old.jpg");
        verifyNoInteractions(storage);
    }

    @Test
    void returnsLegacyUrlWhenObjectKeyIsBlank() {
        MediaUrlResolver resolver = resolver(null, "limit-bucket");

        String result = resolver.resolve("   ", "https://legacy.example.com/old.jpg");

        assertThat(result).isEqualTo("https://legacy.example.com/old.jpg");
    }

    @Test
    void buildsUrlDirectlyFromPublicBaseUrlWhenConfigured() {
        MediaUrlResolver resolver = resolver("https://cdn.example.com/", "limit-bucket");

        String result = resolver.resolve("listings/1/final.jpg", "https://legacy.example.com/old.jpg");

        assertThat(result).isEqualTo("https://cdn.example.com/listings/1/final.jpg");
        verifyNoInteractions(storage);
    }

    @Test
    void fallsBackToLegacyUrlWhenBucketIsNotConfigured() {
        MediaUrlResolver resolver = resolver(null, null);

        String result = resolver.resolve("listings/1/final.jpg", "https://legacy.example.com/old.jpg");

        assertThat(result).isEqualTo("https://legacy.example.com/old.jpg");
        verifyNoInteractions(storage);
    }

    @Test
    void generatesPresignedUrlWhenNoPublicBaseUrlButBucketIsConfigured() throws MalformedURLException {
        MediaUrlResolver resolver = resolver(null, "limit-bucket");
        when(storage.presignGet(eq("limit-bucket"), eq("listings/1/final.jpg"), any(Duration.class)))
                .thenReturn(new URL("https://presigned.example.com/listings/1/final.jpg"));

        String result = resolver.resolve("listings/1/final.jpg", "https://legacy.example.com/old.jpg");

        assertThat(result).isEqualTo("https://presigned.example.com/listings/1/final.jpg");
    }
}
