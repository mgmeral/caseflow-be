package com.caseflow.ticket.api.dto;

import com.caseflow.ticket.domain.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeTicketStatusRequest(

        @NotNull
        TicketStatus status
) {}
