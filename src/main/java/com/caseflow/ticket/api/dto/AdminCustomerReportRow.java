package com.caseflow.ticket.api.dto;

/**
 * One row in the admin cross-customer aggregate report.
 *
 * <p>Status bucket semantics are identical to {@link CustomerTicketReportResponse}.
 * All values reflect the current state of tickets created within the report window.
 */
public record AdminCustomerReportRow(
        Long customerId,
        String customerName,
        long totalCount,
        long openCount,
        long newCount,
        long inProgressCount,
        long waitingCustomerCount,
        long resolvedCount,
        long closedCount,
        long reopenedCount
) {}
