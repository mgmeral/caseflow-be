package com.caseflow.ticket.domain;

import java.time.Duration;

/**
 * Filter preset for SLA state-based ticket listing.
 *
 * <p>Used as a query parameter on {@code GET /api/tickets} to reproduce the exact
 * predicates used by the dashboard SLA metrics. This ensures count/list parity:
 * clicking a dashboard card opens a list filtered with the same predicate.
 *
 * <p>Semantics (both require non-terminal status):
 * <ul>
 *   <li>{@link #BREACHED} — {@code resolutionDueAt IS NOT NULL AND resolutionDueAt < NOW}</li>
 *   <li>{@link #AT_RISK}  — {@code resolutionDueAt IS NOT NULL AND resolutionDueAt > NOW
 *                             AND resolutionDueAt <= NOW + AT_RISK_WINDOW}</li>
 * </ul>
 *
 * <p>{@link #AT_RISK_WINDOW} is the shared constant used by both the dashboard
 * {@code atRiskSlaCount} computation and the filter, guaranteeing count/list parity.
 */
public enum TicketSlaFilter {

    /**
     * Tickets whose resolution SLA has already been breached.
     * Drill-down for the {@code breachedSlaCount} dashboard metric.
     */
    BREACHED,

    /**
     * Tickets whose resolution SLA has not yet breached but is within
     * the {@link #AT_RISK_WINDOW} horizon.
     * Drill-down for the {@code atRiskSlaCount} dashboard metric.
     */
    AT_RISK;

    /**
     * Fixed warning window used by both the dashboard {@code atRiskSlaCount} count
     * and the {@link #AT_RISK} ticket list filter.
     * Centralised here so both computation sites always agree.
     */
    public static final Duration AT_RISK_WINDOW = Duration.ofHours(4);
}
