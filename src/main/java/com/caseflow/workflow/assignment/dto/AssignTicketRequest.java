package com.caseflow.workflow.assignment.dto;

import jakarta.validation.constraints.NotNull;

public record AssignTicketRequest(

        @NotNull
        Long ticketId,

        Long assignedUserId,

        Long assignedGroupId
) {}
