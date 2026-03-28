package com.caseflow.ticket.api.dto;

import java.time.Instant;

public record AttachmentMetadataResponse(
        Long id,
        Long ticketId,
        String emailId,
        String fileName,
        String objectKey,
        String downloadPath,
        String contentType,
        Long size,
        Instant uploadedAt
) {}
