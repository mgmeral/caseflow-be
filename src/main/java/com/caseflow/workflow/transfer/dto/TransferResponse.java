package com.caseflow.workflow.transfer.dto;

import java.time.Instant;

public record TransferResponse(
        Long id,
        Long ticketId,
        Long fromGroupId,
        Long toGroupId,
        String fromGroupName,
        String toGroupName,
        Long transferredBy,
        String transferredByName,
        Instant transferredAt,
        String reason
) {}
