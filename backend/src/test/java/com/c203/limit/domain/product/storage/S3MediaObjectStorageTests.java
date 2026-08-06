package com.c203.limit.domain.product.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class S3MediaObjectStorageTests {

    private static final String BUCKET = "limit-media-test";
    private static final String OBJECT_KEY = "tmp/listing/1/photo.png";
    private static final String PRESIGNED_PUT_URL =
            "https://limit-media-test.s3.example.com/tmp/listing/1/photo.png?signature=dummy";
    private static final String PRESIGNED_GET_URL =
            "https://limit-media-test.s3.example.com/listing/1/photo.png?signature=dummy";

    @Mock S3Client s3Client;
    @Mock S3Presigner presigner;

    @Test
    void presignPutBuildsUploadRequestWithBucketKeyContentTypeAndTtl() {
        S3MediaObjectStorage storage = storage();
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedPut());

        URL url =
                storage.presignPut(
                        BUCKET, OBJECT_KEY, "image/png", 2048L, Duration.ofMinutes(10));

        assertThat(url).hasToString(PRESIGNED_PUT_URL);
        var captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(presigner).presignPutObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(captor.getValue().putObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().putObjectRequest().key()).isEqualTo(OBJECT_KEY);
        assertThat(captor.getValue().putObjectRequest().contentType()).isEqualTo("image/png");
    }

    @Test
    void presignGetBuildsDownloadRequestWithBucketKeyAndTtl() {
        S3MediaObjectStorage storage = storage();
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGet());

        URL url = storage.presignGet(BUCKET, "listing/1/photo.png", Duration.ofMinutes(3));

        assertThat(url).hasToString(PRESIGNED_GET_URL);
        var captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(3));
        assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("listing/1/photo.png");
    }

    @Test
    void headMapsContentLengthAndContentTypeFromS3Response() {
        S3MediaObjectStorage storage = storage();
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(
                        HeadObjectResponse.builder()
                                .contentLength(4096L)
                                .contentType("image/jpeg")
                                .build());

        MediaObjectStorage.StoredObject stored = storage.head(BUCKET, OBJECT_KEY);

        assertThat(stored.contentLength()).isEqualTo(4096L);
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        var captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(OBJECT_KEY);
    }

    @Test
    void headPropagatesMissingObjectException() {
        S3MediaObjectStorage storage = storage();
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        assertThatThrownBy(() -> storage.head(BUCKET, OBJECT_KEY))
                .isInstanceOf(NoSuchKeyException.class);
    }

    @Test
    void promoteCopiesWithReplacedContentTypeThenDeletesSourceObject() {
        S3MediaObjectStorage storage = storage();
        when(s3Client.copyObject(any(CopyObjectRequest.class)))
                .thenReturn(CopyObjectResponse.builder().build());

        storage.promote(BUCKET, OBJECT_KEY, "listing/1/photo.png", "image/png");

        var copyCaptor = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3Client).copyObject(copyCaptor.capture());
        assertThat(copyCaptor.getValue().copySource()).isEqualTo(BUCKET + "/" + OBJECT_KEY);
        assertThat(copyCaptor.getValue().destinationBucket()).isEqualTo(BUCKET);
        assertThat(copyCaptor.getValue().destinationKey()).isEqualTo("listing/1/photo.png");
        assertThat(copyCaptor.getValue().metadataDirective()).isEqualTo(MetadataDirective.REPLACE);
        assertThat(copyCaptor.getValue().contentType()).isEqualTo("image/png");

        var deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(deleteCaptor.getValue().key()).isEqualTo(OBJECT_KEY);
    }

    @Test
    void promoteDoesNotDeleteSourceWhenCopyFails() {
        S3MediaObjectStorage storage = storage();
        when(s3Client.copyObject(any(CopyObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("copy failed").build());

        assertThatThrownBy(
                        () ->
                                storage.promote(
                                        BUCKET, OBJECT_KEY, "listing/1/photo.png", "image/png"))
                .isInstanceOf(S3Exception.class);
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteSendsDeleteRequestForGivenBucketAndKey() {
        S3MediaObjectStorage storage = storage();

        storage.delete(BUCKET, OBJECT_KEY);

        var captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(OBJECT_KEY);
    }

    @Test
    void deletePropagatesSdkExceptionToCaller() {
        S3MediaObjectStorage storage = storage();
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(503).message("slow down").build());

        assertThatThrownBy(() -> storage.delete(BUCKET, OBJECT_KEY))
                .isInstanceOfSatisfying(
                        S3Exception.class,
                        exception -> assertThat(exception.statusCode()).isEqualTo(503));
    }

    private S3MediaObjectStorage storage() {
        return new S3MediaObjectStorage(s3Client, presigner);
    }

    private PresignedPutObjectRequest presignedPut() {
        return PresignedPutObjectRequest.builder()
                .expiration(Instant.parse("2026-08-06T00:10:00Z"))
                .isBrowserExecutable(true)
                .signedHeaders(Map.of("host", List.of("limit-media-test.s3.example.com")))
                .httpRequest(httpRequest(SdkHttpMethod.PUT, PRESIGNED_PUT_URL))
                .build();
    }

    private PresignedGetObjectRequest presignedGet() {
        return PresignedGetObjectRequest.builder()
                .expiration(Instant.parse("2026-08-06T00:03:00Z"))
                .isBrowserExecutable(true)
                .signedHeaders(Map.of("host", List.of("limit-media-test.s3.example.com")))
                .httpRequest(httpRequest(SdkHttpMethod.GET, PRESIGNED_GET_URL))
                .build();
    }

    private SdkHttpRequest httpRequest(SdkHttpMethod httpMethod, String url) {
        return SdkHttpRequest.builder().method(httpMethod).uri(URI.create(url)).build();
    }
}
