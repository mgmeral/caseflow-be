package com.caseflow.ticket.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for bulk ticket assignment.
 * At least one of {@code assignedUserId} or {@code assignedGroupId} must be non-null.
 */
public record BulkAssignRequest(

        @NotEmpty
        @Size(max = 100)
        List<Long> ticketIds,

        /** User to assign to. Null = leave current assignee. */
        Long assignedUserId,

        /** Group to assign to. Null = leave current group. */
        Long assignedGroupId
) {}
