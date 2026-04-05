package com.caseflow.storage;

import com.caseflow.common.exception.AttachmentNotFoundException;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * MinIO (S3-compatible) implementation of ObjectStorageService.
 * Active when caseflow.storage.provider=minio.
 */
@Service
@ConditionalOnProperty(name = "caseflow.storage.provider", havingValue = "minio")
public class MinioStorageService implements ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioClient minioClient;
    private final String bucket;

    public MinioStorageService(StorageProperties properties) {
        StorageProperties.MinioProperties m = properties.getMinio();
        this.bucket = m.getBucket();
        this.minioClient = MinioClient.builder()
                .endpoint(m.getEndpoint())
                .credentials(m.getAccessKey(), m.getSecretKey())
                .build();
        ensureBucketExists();
    }

    @Override
    public String store(String objectKey, byte[] data, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
            log.debug("Stored object in MinIO: {}", objectKey);
            return objectKey;
        } catch (Exception e) {
            throw new RuntimeException("Failed to store object: " + objectKey, e);
        }
    }

    @Override
    public InputStream retrieve(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new AttachmentNotFoundException(objectKey);
            }
            throw new RuntimeException("Failed to retrieve object: " + objectKey, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve object: " + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            log.debug("Deleted object from MinIO: {}", objectKey);
        } catch (Exception e) {
            log.warn("Failed to delete object from MinIO: {}", objectKey, e);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new RuntimeException("Failed to check object existence: " + objectKey, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to check object existence: " + objectKey, e);
        }
    }

    @Override
    public void copy(String sourceKey, String destKey) {
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucket)
                    .object(destKey)
                    .source(CopySource.builder().bucket(bucket).object(sourceKey).build())
                    .build());
            log.debug("Copied object in MinIO: {} → {}", sourceKey, destKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy object: " + sourceKey + " → " + destKey, e);
        }
    }

    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            } else {
                log.info("MinIO bucket already exists: {}", bucket);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise MinIO bucket: " + bucket, e);
        }
    }
}
