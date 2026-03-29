package com.caseflow.email.service;

/**
 * Carries metadata for a single attachment extracted from an inbound IMAP message.
 *
 * <p>The binary content has already been stored in object storage by the time this record is
 * created.  {@code objectKey} is the storage path used to retrieve or reference the file.
 */
public record IngressAttachmentData(
        String fileName,
        String objectKey,
        String contentType,
        Long size
) {}
