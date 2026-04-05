package com.caseflow.storage;

import java.io.InputStream;

/**
 * Provider-agnostic object storage abstraction.
 * Local filesystem implementation is the default for dev.
 * Replace or extend with S3/MinIO/Azure Blob as needed.
 */
public interface ObjectStorageService {

    /**
     * Store bytes under the given object key.
     *
     * @param objectKey   unique storage path, e.g. "attachments/ticket-42/report.pdf"
     * @param data        raw file content
     * @param contentType MIME type
     * @return the objectKey as stored (unchanged in most implementations)
     */
    String store(String objectKey, byte[] data, String contentType);

    /**
     * Retrieve stored content as a stream. Caller is responsible for closing the stream.
     *
     * @throws com.caseflow.common.exception.AttachmentNotFoundException if key not found
     */
    InputStream retrieve(String objectKey);

    /**
     * Delete the object. No-op if it does not exist.
     */
    void delete(String objectKey);

    /**
     * @return true if the given key exists in storage
     */
    boolean exists(String objectKey);

    /**
     * Copies an object from {@code sourceKey} to {@code destKey} within the same store.
     * Used when promoting attachments from a staging path to a final canonical path.
     *
     * <p>If {@code destKey} already exists it is replaced. If {@code sourceKey} does not exist
     * the behaviour is implementation-defined but must not throw silently — callers should
     * handle {@link com.caseflow.common.exception.AttachmentNotFoundException}.
     */
    void copy(String sourceKey, String destKey);
}
