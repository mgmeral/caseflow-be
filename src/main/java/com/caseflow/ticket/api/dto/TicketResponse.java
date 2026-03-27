package com.caseflow.ticket.api.dto;

import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;

import java.time.Instant;

public record TicketResponse(
        Long id,
        String ticketNo,
        String subject,
        String description,
        TicketStatus status,
        TicketPriority priority,
        Long customerId,
        Long assignedUserId,
        Long assignedGroupId,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {}
