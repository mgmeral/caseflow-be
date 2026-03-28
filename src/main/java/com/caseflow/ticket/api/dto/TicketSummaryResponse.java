package com.caseflow.ticket.api.dto;

import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;

import java.time.Instant;

public record TicketSummaryResponse(
        Long id,
        String ticketNo,
        String subject,
        TicketStatus status,
        TicketPriority priority,
        Long customerId,
        String customerName,
        Long assignedUserId,
        String assignedUserName,
        Long assignedGroupId,
        String assignedGroupName,
        Instant createdAt,
        Instant updatedAt
) {}
