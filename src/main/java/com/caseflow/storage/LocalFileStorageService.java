package com.caseflow.storage;

import com.caseflow.common.exception.AttachmentNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local filesystem implementation of ObjectStorageService.
 * Suitable for development and single-node deployments.
 * All files are stored under ${caseflow.storage.root-path}.
 */
@Service
@ConditionalOnProperty(name = "caseflow.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final Path rootPath;

    public LocalFileStorageService(StorageProperties properties) {
        this.rootPath = Paths.get(properties.getRootPath()).toAbsolutePath().normalize();
        ensureRootExists();
    }

    @Override
    public String store(String objectKey, byte[] data, String contentType) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            log.debug("Stored object: {}", objectKey);
            return objectKey;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store object: " + objectKey, e);
        }
    }

    @Override
    public InputStream retrieve(String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.exists(target)) {
            throw new AttachmentNotFoundException(objectKey);
        }
        try {
            return new ByteArrayInputStream(Files.readAllBytes(target));
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve object: " + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        Path target = resolve(objectKey);
        try {
            Files.deleteIfExists(target);
            log.debug("Deleted object: {}", objectKey);
        } catch (IOException e) {
            log.warn("Failed to delete object: {}", objectKey, e);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.exists(resolve(objectKey));
    }

    @Override
    public void copy(String sourceKey, String destKey) {
        Path source = resolve(sourceKey);
        Path dest = resolve(destKey);
        try {
            Files.createDirectories(dest.getParent());
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Copied object: {} → {}", sourceKey, destKey);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy object: " + sourceKey + " → " + destKey, e);
        }
    }

    private Path resolve(String objectKey) {
        // Sanitize: prevent path traversal
        String sanitized = objectKey.replace("..", "").replaceAll("^/+", "");
        return rootPath.resolve(sanitized).normalize();
    }

    private void ensureRootExists() {
        try {
            Files.createDirectories(rootPath);
            log.info("Local storage root: {}", rootPath);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create storage root at: " + rootPath, e);
        }
    }
}
