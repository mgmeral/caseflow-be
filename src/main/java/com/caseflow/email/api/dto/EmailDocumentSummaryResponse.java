package com.caseflow.email.api.dto;

import java.time.Instant;

public record EmailDocumentSummaryResponse(
        String id,
        String messageId,
        String subject,
        String from,
        Instant receivedAt,
        Long ticketId
) {}
