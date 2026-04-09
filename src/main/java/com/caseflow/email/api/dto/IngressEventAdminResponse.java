package com.caseflow.email.api.dto;

import com.caseflow.email.domain.EmailIngressEvent;

import java.time.Instant;

/**
 * Operator-facing ingress event summary.
 * Includes all fields needed to identify, diagnose, and act on stuck or failed events.
 */
public record IngressEventAdminResponse(
        Long id,
        Long mailboxId,
        String messageId,
        String rawFrom,
        String rawSubject,
        String status,
        String failureReason,
        Integer processingAttempts,
        Long ticketId,
        String documentId,
        Instant receivedAt,
        Instant lastAttemptAt,
        Instant processedAt
) {
    public static IngressEventAdminResponse from(EmailIngressEvent e) {
        return new IngressEventAdminResponse(
                e.getId(),
                e.getMailboxId(),
                e.getMessageId(),
                e.getRawFrom(),
                e.getRawSubject(),
                e.getStatus().name(),
                e.getFailureReason(),
                e.getProcessingAttempts(),
                e.getTicketId(),
                e.getDocumentId(),
                e.getReceivedAt(),
                e.getLastAttemptAt(),
                e.getProcessedAt()
        );
    }
}
