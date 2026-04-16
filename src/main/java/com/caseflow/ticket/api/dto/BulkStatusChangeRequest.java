package com.caseflow.ticket.api.dto;

import com.caseflow.ticket.domain.TicketStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request body for bulk ticket status change. Invalid transitions are reported in the failure list. */
public record BulkStatusChangeRequest(

        @NotEmpty
        @Size(max = 100)
        List<Long> ticketIds,

        @NotNull
        TicketStatus newStatus
) {}
