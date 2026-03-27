package com.caseflow.workflow.assignment.dto;

import jakarta.validation.constraints.NotNull;

public record UnassignTicketRequest(
        @NotNull Long ticketId
) {}
