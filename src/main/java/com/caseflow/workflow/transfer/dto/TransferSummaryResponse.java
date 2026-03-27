package com.caseflow.workflow.transfer.dto;

import java.time.Instant;

public record TransferSummaryResponse(
        Long id,
        Long ticketId,
        Long fromGroupId,
        Long toGroupId,
        Instant transferredAt
) {}
