package com.caseflow.ticket.api.dto;

import com.caseflow.sla.domain.SlaState;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public record TicketSummaryResponse(
        Long id,
        UUID publicId,
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
        Instant updatedAt,
        /** When the current status was entered. Null for tickets pre-dating V28 migration. */
        Instant statusChangedAt,
        /** Lightweight SLA state for list/queue views. Null when no policy is configured. */
        SlaState slaState,
        /** When first-response SLA target expires. Null when no policy is configured. */
        Instant firstResponseDueAt,
        /** When resolution SLA target expires. Null when no policy is configured. */
        Instant resolutionDueAt
) {}
