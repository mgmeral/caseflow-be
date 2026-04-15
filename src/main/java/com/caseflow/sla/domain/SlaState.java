package com.caseflow.sla.domain;

/**
 * Runtime SLA state for a ticket at a given moment.
 *
 * <ul>
 *   <li>{@link #OK} — within SLA target, no warning yet</li>
 *   <li>{@link #WARNING} — past the warning threshold, still within target</li>
 *   <li>{@link #BREACHED} — target exceeded, first-response or resolution SLA broken</li>
 *   <li>{@link #RESOLVED} — ticket reached a terminal state; SLA clock stopped</li>
 *   <li>{@link #PAUSED} — SLA clock is suspended (WAITING_CUSTOMER); not yet active</li>
 * </ul>
 */
public enum SlaState {
    OK,
    WARNING,
    BREACHED,
    RESOLVED,
    PAUSED
}
