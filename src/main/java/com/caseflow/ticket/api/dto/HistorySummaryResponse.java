package com.caseflow.ticket.api.dto;

import java.time.Instant;

public record HistorySummaryResponse(
        Long id,
        String actionType,
        Long performedBy,
        Instant performedAt
) {}
