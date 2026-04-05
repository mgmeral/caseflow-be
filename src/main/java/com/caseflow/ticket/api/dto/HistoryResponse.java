package com.caseflow.ticket.api.dto;

import java.time.Instant;
import java.util.UUID;

public record HistoryResponse(
        Long id,
        Long ticketId,
        UUID ticketPublicId,
        String actionType,
        Long performedBy,
        String performedByName,
        String sourceType,
        String summary,
        String details,
        String oldValueJson,
        String newValueJson,
        String metadataJson,
        Instant performedAt
) {}
