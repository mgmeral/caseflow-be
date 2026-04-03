package com.caseflow.email.api.dto;

import java.time.Instant;

/**
 * Raw/debug view of an inbound email document.
 * Exposes the original, unsanitized HTML body — for audit and operator debugging only.
 * Never use this as the default operator-facing view; see {@link EmailDocumentResponse}
 * for the sanitized version.
 */
public record EmailDocumentRawResponse(
        String id,
        String messageId,
        String subject,
        String from,
        Instant receivedAt,
        Long ticketId,
        String textBody,
        /** Raw HTML body exactly as received — NOT sanitized. Debug/audit use only. */
        String htmlBody
) {}
