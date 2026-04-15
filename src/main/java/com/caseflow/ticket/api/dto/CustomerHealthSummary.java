package com.caseflow.ticket.api.dto;

/**
 * Health summary for a single customer.
 *
 * <h2>Health score rules (backend-authoritative)</h2>
 * <ul>
 *   <li>{@code AT_RISK}  — any breached SLA ticket, or open tickets &gt; 20</li>
 *   <li>{@code WATCH}    — avgFirstResponseMinutes &gt; 60, or open tickets &gt; 10</li>
 *   <li>{@code STABLE}   — all other cases</li>
 * </ul>
 *
 * <p>FE must not re-derive the health score from the raw counts — always use the
 * {@code healthScore} field returned by this response.
 */
public record CustomerHealthSummary(
        Long customerId,
        String customerName,
        String colorHex,
        long openTickets,
        long breachedSlaCount,
        /** Average first-response time in minutes. Null if no data in window. */
        Long avgFirstResponseMinutes,
        /** Average resolution time in minutes. Null if no data in window. */
        Long avgResolutionMinutes,
        HealthScore healthScore
) {

    public enum HealthScore { STABLE, WATCH, AT_RISK }
}
