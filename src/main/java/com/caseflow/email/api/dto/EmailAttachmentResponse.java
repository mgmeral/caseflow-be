package com.caseflow.email.api.dto;

public record EmailAttachmentResponse(
        String fileName,
        String objectKey,
        String contentType,
        Long size
) {}
