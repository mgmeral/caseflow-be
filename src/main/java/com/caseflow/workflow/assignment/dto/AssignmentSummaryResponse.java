package com.caseflow.workflow.assignment.dto;

import java.time.Instant;

public record AssignmentSummaryResponse(
        Long id,
        Long ticketId,
        Long assignedUserId,
        Long assignedGroupId,
        Instant assignedAt,
        boolean active
) {}
