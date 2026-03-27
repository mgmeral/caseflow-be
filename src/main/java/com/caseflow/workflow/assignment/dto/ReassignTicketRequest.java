package com.caseflow.workflow.assignment.dto;

import jakarta.validation.constraints.NotNull;

public record ReassignTicketRequest(

        @NotNull
        Long ticketId,

        Long newUserId,

        Long newGroupId,

        @NotNull
        Long reassignedBy
) {}
