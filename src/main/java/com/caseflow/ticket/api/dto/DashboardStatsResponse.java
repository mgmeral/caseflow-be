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
 *   <li>{@code breachedSlaCount} — non-terminal tickets where {@code resolutionDueAt} has already passed</li>
 *   <li>{@code atRiskSlaCount} — non-terminal tickets where {@code resolutionDueAt} has NOT passed yet
 *       but is within the next 4 hours (breach imminent). Complement of {@code breachedSlaCount}.</li>
 *   <li>{@code myActionRequired} — assigned to current user AND status NOT IN (RESOLVED, CLOSED).
 *       Only populated when a user context is provided; null otherwise.</li>
 * </ul>
 *
 * <p>Drill-down filter contract — how to reproduce each count via {@code GET /api/tickets}:
 * <ul>
 *   <li>{@code activeTickets}      → {@code openOnly=true}</li>
 *   <li>{@code unassignedTickets}  → {@code openOnly=true&unassignedOnly=true}</li>
 *   <li>{@code waitingOver24h}     → {@code openOnly=true&staleOpenOverHours=24}</li>
 *   <li>{@code breachedSlaCount}   → {@code slaState=BREACHED}</li>
 *   <li>{@code atRiskSlaCount}     → {@code slaState=AT_RISK}</li>
 *   <li>{@code resolvedTickets}    → {@code status=RESOLVED}</li>
 *   <li>{@code closedTickets}      → {@code status=CLOSED}</li>
 * </ul>
 *
 * <p>The {@code slaState} drill-down uses the identical predicate as the dashboard count.
 * For {@code AT_RISK}, the fixed 4-hour window is defined in
 * {@link com.caseflow.ticket.domain.TicketSlaFilter#AT_RISK_WINDOW}.
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
         * Drill-down: GET /api/tickets?openOnly=true&staleOpenOverHours=24
         */
        long waitingOver24h,
        /**
         * Open tickets whose {@code resolutionDueAt} has passed — real SLA breach count.
         * Uses the SLA model's {@code resolutionDueAt} field set at ticket creation.
         */
        long breachedSlaCount,
        /**
         * Open tickets whose {@code resolutionDueAt} has NOT yet passed but is within the next 4 hours.
         * These are "at risk" — breach is imminent. Computed with a fixed 4-hour warning window
         * at the dashboard level; per-policy thresholds apply in the SLA detail view.
         */
        long atRiskSlaCount,
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
