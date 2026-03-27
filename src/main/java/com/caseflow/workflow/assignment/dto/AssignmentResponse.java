package com.caseflow.workflow.assignment.dto;

import java.time.Instant;

public record AssignmentResponse(
        Long id,
        Long ticketId,
        Long assignedUserId,
        Long assignedGroupId,
        Long assignedBy,
        Instant assignedAt,
        Instant unassignedAt,
        boolean active
) {}
