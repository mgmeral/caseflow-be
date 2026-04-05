package com.caseflow.ticket.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Ticket report for a single customer.
 *
 * <h2>Status bucket semantics</h2>
 * All buckets are defined by the backend. FE must not derive buckets from raw status values.
 * <ul>
 *   <li>{@code newCount} — tickets currently in NEW status</li>
 *   <li>{@code inProgressCount} — tickets in TRIAGED, ASSIGNED, or IN_PROGRESS</li>
 *   <li>{@code waitingCustomerCount} — tickets in WAITING_CUSTOMER</li>
 *   <li>{@code resolvedCount} — tickets in RESOLVED</li>
 *   <li>{@code closedCount} — tickets in CLOSED</li>
 *   <li>{@code reopenedCount} — tickets in REOPENED</li>
 *   <li>{@code openCount} — all non-final statuses (NEW + TRIAGED + ASSIGNED + IN_PROGRESS + WAITING_CUSTOMER + REOPENED)</li>
 *   <li>{@code totalCount} — all tickets in the date range</li>
 * </ul>
 *
 * <h2>Date range semantics</h2>
 * Counts are for tickets created within [from, to].
 * Status buckets reflect each ticket's current status at report generation time.
 */
public record CustomerTicketReportResponse(

        Long customerId,
        String customerName,

        /** ISO-8601 start of the report window (inclusive). */
        Instant from,

        /** ISO-8601 end of the report window (inclusive). */
        Instant to,

        long totalCount,
        long openCount,
        long newCount,
        long inProgressCount,
        long waitingCustomerCount,
        long resolvedCount,
        long closedCount,
        long reopenedCount,

        /** Tag breakdown — count of tickets (in date range) carrying each tag. */
        List<TagCount> byTag

) {
    public record TagCount(Long tagId, String tagCode, String tagName, long count) {}
}
