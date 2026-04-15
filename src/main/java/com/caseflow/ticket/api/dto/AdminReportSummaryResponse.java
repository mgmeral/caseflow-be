package com.caseflow.ticket.api.dto;

import java.time.Instant;

/**
 * Executive-level summary for admin ticket reports.
 *
 * <p>All metrics are computed for the requested date window and customer scope.
 * Designed for management summary cards and CSV/PDF export headers.
 *
 * <h2>Timing fields</h2>
 * {@code avgFirstResponseMinutes} and {@code avgResolutionMinutes} are computed from
 * tickets that have first-response and resolution timestamps respectively.
 * Null means not enough data is available for the time window.
 *
 * <h2>Rate fields</h2>
 * {@code reopenRate} = reopened / (resolved + closed + reopened) within the window.
 * {@code transferRate} = tickets with at least one transfer / totalTickets.
 * {@code waitingCustomerRatio} = WAITING_CUSTOMER / activeTickets.
 */
public record AdminReportSummaryResponse(

        Instant from,
        Instant to,

        // ── Volume ────────────────────────────────────────────────────────────
        long totalTickets,
        long activeTickets,
        long resolvedTickets,
        long closedTickets,
        long reopenedTickets,
        long waitingCustomerTickets,

        // ── SLA / timing ──────────────────────────────────────────────────────
        /** Tickets with resolution SLA breached (resolutionDueAt exceeded, still open). */
        long breachedTickets,
        /** Average first-response time in minutes. Null if insufficient data. */
        Long avgFirstResponseMinutes,
        /** Average resolution time in minutes. Null if insufficient data. */
        Long avgResolutionMinutes,

        // ── Rates ─────────────────────────────────────────────────────────────
        /** reopened / (resolved + closed + reopened). Null if denominator is zero. */
        Double reopenRate,
        /** tickets with ≥1 transfer / totalTickets. Null if no tickets. */
        Double transferRate,
        /** waitingCustomer / activeTickets. Null if no active tickets. */
        Double waitingCustomerRatio,
        /** (resolved + closed) / totalTickets. Null if no tickets. */
        Double terminalRatio
) {}
