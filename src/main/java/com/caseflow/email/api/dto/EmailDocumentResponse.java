package com.caseflow.email.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Operator-facing email body view.
 * {@code sanitizedHtmlBody} has been cleaned through Jsoup's relaxed safelist — safe to render.
 * {@code textBody} is the plain-text fallback when no HTML is present.
 * Raw HTML is intentionally excluded; use {@code GET /api/emails/{id}/raw} for audit/debug.
 */
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
        String sanitizedHtmlBody,
        List<EmailAttachmentResponse> attachments
) {}
