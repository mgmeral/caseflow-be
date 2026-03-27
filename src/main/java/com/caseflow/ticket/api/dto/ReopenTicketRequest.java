package com.caseflow.ticket.api.dto;

import jakarta.validation.constraints.NotNull;

public record ReopenTicketRequest(

        @NotNull
        Long performedBy
) {}
