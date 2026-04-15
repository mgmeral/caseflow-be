package com.caseflow.ticket.api.dto;

import java.util.List;

/**
 * Agent and group workload aggregates.
 *
 * <p>Provides per-assignee and per-group views of active ticket load,
 * including breach risk signals for triage prioritisation.
 */
public record WorkloadSummaryResponse(
        List<AssigneeWorkload> byAssignee,
        List<GroupWorkload> byGroup
) {

    /**
     * Per-agent workload.
     *
     * @param userId            agent user ID
     * @param username          agent username
     * @param activeCount       active tickets assigned to this agent (non-terminal)
     * @param waitingCustomerCount tickets in WAITING_CUSTOMER assigned to this agent
     * @param breachedCount     tickets where resolutionDueAt is exceeded
     */
    public record AssigneeWorkload(
            Long userId,
            String username,
            long activeCount,
            long waitingCustomerCount,
            long breachedCount
    ) {}

    /**
     * Per-group workload.
     *
     * @param groupId       group ID
     * @param groupName     group display name
     * @param activeCount   active tickets in this group (non-terminal, any assignment)
     * @param unassignedCount active tickets in this group with no user assigned
     * @param breachedCount tickets in this group where resolutionDueAt is exceeded
     */
    public record GroupWorkload(
            Long groupId,
            String groupName,
            long activeCount,
            long unassignedCount,
            long breachedCount
    ) {}
}
