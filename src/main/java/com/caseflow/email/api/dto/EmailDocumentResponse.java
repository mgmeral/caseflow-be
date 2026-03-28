package com.caseflow.email.api.dto;

import java.time.Instant;
import java.util.List;

public record EmailDocumentResponse(
        String id,
        String messageId,
        String threadKey,
        String subject,
        String from,
        List<String> to,
        List<String> cc,
        Instant receivedAt,
        Instant parsedAt,
        Long ticketId,
        String textBody,
        String htmlBody,
        List<EmailAttachmentResponse> attachments
) {}
