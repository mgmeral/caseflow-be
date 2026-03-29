package com.caseflow.email.api.dto;

import java.time.Instant;

/**
 * Unified view of a single item in a ticket's email thread.
 * Combines inbound events and outbound dispatches into a chronological timeline.
 */
public record EmailThreadItem(
        /** "INBOUND" or "OUTBOUND" */
        String direction,
        Long id,
        String messageId,
        String fromAddress,
        String toAddress,
        String subject,
        /** String form of IngressEventStatus (INBOUND) or DispatchStatus (OUTBOUND) */
        String status,
        /** receivedAt for INBOUND, sentAt or createdAt for OUTBOUND */
        Instant timestamp
) {}
