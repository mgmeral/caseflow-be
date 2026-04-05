package com.caseflow.ticket.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AttachmentMetadataResponse(
        Long id,
        Long ticketId,
        UUID ticketPublicId,
        String emailId,
        String fileName,
        String contentType,
        Long size,
        String sourceType,
        /** Download path — relative URL, safe to pass to the client. Omits raw bucket key. */
        String downloadPath,
        boolean previewSupported,
        Instant uploadedAt
) {}
