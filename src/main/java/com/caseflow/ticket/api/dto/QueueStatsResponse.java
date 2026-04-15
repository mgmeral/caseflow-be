package com.caseflow.ticket.api.dto;

/**
 * Queue membership counts by predefined chip categories.
 *
 * <p>All counts are derived from the same queue membership predicate:
 * unassigned (no assignedUserId) AND status NOT IN (RESOLVED, CLOSED).
 * Each chip further narrows that base predicate.
 *
 * <p>These counts match the queue list rows — there is no separate counting logic.
 *
 * <p>Timing semantics: waiting and SLA metrics use COALESCE(statusChangedAt, createdAt),
 * not updatedAt, to avoid false-positives from non-workflow row updates.
 */
public record QueueStatsResponse(
        /** Total unassigned, non-terminal tickets. */
        long allUnassigned,
        /** Subset with priority HIGH or CRITICAL. */
        long highOrCritical,
        /**
         * Subset where COALESCE(statusChangedAt, createdAt) is older than 8 hours.
         * Measures how long the ticket has been in its current status, not the last row update.
         */
        long waitingOver8h,
        /**
         * Unassigned tickets whose {@code resolutionDueAt} has passed — real SLA breach count.
         * Uses the SLA model's {@code resolutionDueAt} field set at ticket creation.
         */
        long slaBreached
) {}
