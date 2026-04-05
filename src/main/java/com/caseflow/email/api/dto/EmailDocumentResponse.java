package com.caseflow.email.api.dto;

import com.caseflow.ticket.api.dto.AttachmentMetadataResponse;

import java.time.Instant;
import java.util.List;

/**
 * Operator-facing email body view.
 * {@code sanitizedHtmlBody} has been cleaned through Jsoup's relaxed safelist — safe to render.
 * {@code textBody} is the plain-text fallback when no HTML is present.
 * Raw HTML is intentionally excluded; use {@code GET /api/emails/{id}/raw} for audit/debug.
 * {@code attachments} maps to JPA {@code AttachmentMetadata} records so each entry carries
 * a stable {@code id} and a usable {@code downloadPath}.
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
        List<AttachmentMetadataResponse> attachments
) {}
