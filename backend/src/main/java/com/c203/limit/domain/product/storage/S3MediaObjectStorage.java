package com.c203.limit.domain.product.storage;

import java.net.URL;
import java.time.Duration;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
public class S3MediaObjectStorage implements MediaObjectStorage {

    private final S3Client s3Client;
    private final S3Presigner presigner;

    public S3MediaObjectStorage(S3Client mediaS3Client, S3Presigner mediaS3Presigner) {
        this.s3Client = mediaS3Client;
        this.presigner = mediaS3Presigner;
    }

    @Override
    public URL presignPut(
            String bucket, String objectKey, String contentType, long contentLength, Duration ttl) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .putObjectRequest(request)
                        .build())
                .url();
    }

    @Override
    public URL presignGet(String bucket, String objectKey, Duration ttl) {
        GetObjectRequest request =
                GetObjectRequest.builder().bucket(bucket).key(objectKey).build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(request)
                        .build())
                .url();
    }

    @Override
    public StoredObject head(String bucket, String objectKey) {
        var response = s3Client.headObject(
                HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
        return new StoredObject(response.contentLength(), response.contentType());
    }

    @Override
    public void promote(
            String bucket, String sourceKey, String destinationKey, String contentType) {
        s3Client.copyObject(CopyObjectRequest.builder()
                .copySource(bucket + "/" + sourceKey)
                .destinationBucket(bucket)
                .destinationKey(destinationKey)
                .metadataDirective(MetadataDirective.REPLACE)
                .contentType(contentType)
                .build());
        s3Client.deleteObject(
                DeleteObjectRequest.builder().bucket(bucket).key(sourceKey).build());
    }

    @Override
    public void delete(String bucket, String objectKey) {
        s3Client.deleteObject(
                DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
    }
}
