package com.caseflow.workflow.transfer.dto;

import jakarta.validation.constraints.NotNull;

public record TransferTicketRequest(

        @NotNull
        Long ticketId,

        @NotNull
        Long fromGroupId,

        @NotNull
        Long toGroupId,

        @NotNull
        Long transferredBy,

        String reason,

        boolean clearAssignee
) {}
