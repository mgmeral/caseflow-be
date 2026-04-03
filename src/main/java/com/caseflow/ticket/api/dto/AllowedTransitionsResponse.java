package com.caseflow.ticket.api.dto;

import com.caseflow.ticket.domain.TicketStatus;

import java.util.Set;

/**
 * FE-facing response describing which status transitions are currently valid for a ticket.
 */
public record AllowedTransitionsResponse(
        Long ticketId,
        TicketStatus currentStatus,
        Set<TicketStatus> allowedTransitions
) {}
