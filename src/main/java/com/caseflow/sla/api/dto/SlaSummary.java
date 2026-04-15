package com.caseflow.sla.api.dto;

import com.caseflow.sla.domain.SlaState;

import java.time.Instant;

/**
 * Computed SLA state for a single ticket at a point in time.
 *
 * <h2>Rule definitions (coded, not prose)</h2>
 * <ul>
 *   <li>First-response: met when {@code firstResponseRespondedAt != null} before {@code firstResponseDueAt}.</li>
 *   <li>Resolution: met when ticket reaches RESOLVED or CLOSED before {@code resolutionDueAt}.</li>
 *   <li>State precedence: BREACHED > WARNING > PAUSED > OK > RESOLVED</li>
 * </ul>
 */
public record SlaSummary(
        /** When first-response SLA target expires. Null if no policy was applied. */
        Instant firstResponseDueAt,
        /** When resolution SLA target expires. Null if no policy was applied. */
        Instant resolutionDueAt,
        /** When the first customer-visible outbound reply was sent. Null if not yet sent. */
        Instant firstResponseRespondedAt,
        /** True if the first-response SLA target was exceeded before a reply was sent. */
        boolean firstResponseBreached,
        /** True if the resolution SLA target was exceeded while the ticket is still open. */
        boolean resolutionBreached,
        /** Composite SLA state reflecting the worst of first-response and resolution. */
        SlaState slaState,
        /** Age of the ticket in minutes from creation to now (or terminal time). */
        long ageMinutes,
        /** Minutes the ticket has been in its current status. */
        long currentStatusAgeMinutes
) {}
