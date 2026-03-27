package com.caseflow.ticket.api.dto;

import com.caseflow.ticket.domain.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTicketRequest(

        @NotBlank
        @Size(max = 255)
        String subject,

        String description,

        @NotNull
        TicketPriority priority
) {}
