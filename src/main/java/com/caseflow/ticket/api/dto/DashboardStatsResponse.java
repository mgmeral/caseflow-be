package com.caseflow.ticket.api.dto;

import java.util.List;

/**
 * Dashboard aggregate statistics response.
 *
 * <p>All counts share the same ticket truth source (the tickets table) and use
 * explicit, documented business rules. Related lists/widgets must use the same predicates.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code totalTickets} — all tickets regardless of status</li>
 *   <li>{@code activeTickets} — status NOT IN (RESOLVED, CLOSED)</li>
 *   <li>{@code resolvedTickets} — status = RESOLVED</li>
 *   <li>{@code closedTickets} — status = CLOSED</li>
 *   <li>{@code unassignedTickets} — assignedUserId IS NULL AND status NOT IN (RESOLVED, CLOSED)</li>
 *   <li>{@code waitingOver24h} — COALESCE(statusChangedAt, createdAt) older than 24 hours AND status NOT IN (RESOLVED, CLOSED)</li>
 *   <li>{@code myActionRequired} — assigned to current user AND status NOT IN (RESOLVED, CLOSED).
 *       Only populated when a user context is provided; null otherwise.</li>
 * </ul>
 */
public record DashboardStatsResponse(
        long totalTickets,
        long activeTickets,
        long resolvedTickets,
        long closedTickets,
        long unassignedTickets,
        /**
         * Active tickets (non-terminal) where COALESCE(statusChangedAt, createdAt) is older than 24 hours.
         * Uses statusChangedAt so that note creation or other non-workflow updates do not reset the clock.
         */
        long waitingOver24h,
        Long myActionRequired,
        List<MyActionRequiredItem> myActionRequiredItems
) {

    /** Compact ticket reference for the myActionRequired widget. */
    public record MyActionRequiredItem(
            Long id,
            String ticketNo,
            String subject,
            String status,
            String priority,
            String customerName
    ) {}
}
