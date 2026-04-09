package com.caseflow.email.scheduled.api.dto;

import com.caseflow.email.domain.OutboundEmailDispatch;

import java.time.Instant;

/**
 * Response contract for a scheduled outbound email dispatch.
 *
 * <p>Exposes all fields needed for the operator to understand thread context,
 * delivery state, and failure reason without additional lookups.
 */
public record ScheduledEmailResponse(
        Long id,
        Long ticketId,
        Long mailboxId,
        /** Sender address — the mailbox address used for outbound SMTP. */
        String fromAddress,
        /** Backend-resolved recipient (from source event Reply-To/From, or explicit override). */
        String resolvedToAddress,
        /** The inbound event this scheduled email replies to; null for proactive sends. */
        Long sourceEventId,
        String subject,
        String status,
        String failureReason,
        String failureCategory,
        Instant sendNotBefore,
        Instant createdAt,
        Instant sentAt,
        Instant canceledAt
) {
    public static ScheduledEmailResponse from(OutboundEmailDispatch d) {
        return new ScheduledEmailResponse(
                d.getId(),
                d.getTicketId(),
                d.getMailboxId(),
                d.getFromAddress(),
                d.getResolvedToAddress() != null ? d.getResolvedToAddress() : d.getToAddress(),
                d.getSourceIngressEventId(),
                d.getSubject(),
                d.getStatus().name(),
                d.getFailureReason(),
                d.getFailureCategory() != null ? d.getFailureCategory().name() : null,
                d.getScheduledAt(),
                d.getCreatedAt(),
                d.getSentAt(),
                d.getCanceledAt()
        );
    }
}
