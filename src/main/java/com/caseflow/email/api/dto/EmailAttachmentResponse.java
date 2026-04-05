package com.caseflow.email.api.dto;

/**
 * Attachment metadata included in email detail responses.
 *
 * <p>The raw object-storage key is intentionally omitted — callers should use the
 * dedicated attachment content endpoint to retrieve file data.
 */
public record EmailAttachmentResponse(
        String fileName,
        String contentType,
        Long size,
        boolean previewSupported
) {}
