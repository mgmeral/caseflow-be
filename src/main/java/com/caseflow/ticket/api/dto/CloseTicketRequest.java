package com.caseflow.ticket.api.dto;

import jakarta.validation.constraints.NotNull;

public record CloseTicketRequest(

        @NotNull
        Long performedBy
) {}
