package com.caseflow.workflow.transfer.dto;

import java.time.Instant;

public record TransferResponse(
        Long id,
        Long ticketId,
        Long fromGroupId,
        Long toGroupId,
        Long transferredBy,
        Instant transferredAt,
        String reason
) {}
