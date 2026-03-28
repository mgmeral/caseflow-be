package com.caseflow.ticket.api.dto;

import java.time.Instant;

public record HistoryResponse(
        Long id,
        Long ticketId,
        String actionType,
        Long performedBy,
        String performedByName,
        Instant performedAt,
        String details
) {}
