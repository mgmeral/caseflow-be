package com.caseflow.ticket.api.dto;

import java.time.Instant;

/**
 * A tag assigned to a specific ticket.
 * Returned by GET /api/tickets/{id}/tags.
 */
public record TicketTagResponse(
        Long tagId,
        String tagCode,
        String tagName,
        String tagColor,
        Instant taggedAt,
        Long taggedBy
) {}
