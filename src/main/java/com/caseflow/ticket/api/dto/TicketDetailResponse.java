package com.caseflow.ticket.api.dto;

import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;

import java.time.Instant;
import java.util.List;

public record TicketDetailResponse(
        Long id,
        String ticketNo,
        String subject,
        String description,
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
        Instant closedAt,
        List<AttachmentMetadataResponse> attachments,
        List<HistorySummaryResponse> history
) {}
