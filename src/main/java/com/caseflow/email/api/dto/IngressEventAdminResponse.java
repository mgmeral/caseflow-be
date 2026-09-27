package com.caseflow.email.api.dto;

import com.caseflow.email.api.PiiMasker;
import com.caseflow.email.domain.EmailIngressEvent;

import java.time.Instant;

/**
 * Operator-facing ingress event summary.
 * Includes all fields needed to identify, diagnose, and act on stuck or failed events.
 *
 * <p>{@code masked = true} means {@code rawFrom}, {@code rawSubject} and {@code failureReason}
 * went through {@link PiiMasker}. Lists are always masked; the full values are only returned
 * by the detail endpoint to operators who may act on the event.
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
        Instant processedAt,
        boolean masked
) {
    public static IngressEventAdminResponse from(EmailIngressEvent e) {
        return of(e, false);
    }

    public static IngressEventAdminResponse masked(EmailIngressEvent e) {
        return of(e, true);
    }

    private static IngressEventAdminResponse of(EmailIngressEvent e, boolean mask) {
        return new IngressEventAdminResponse(
                e.getId(),
                e.getMailboxId(),
                e.getMessageId(),
                mask ? PiiMasker.mask(e.getRawFrom()) : e.getRawFrom(),
                mask ? PiiMasker.mask(e.getRawSubject()) : e.getRawSubject(),
                e.getStatus().name(),
                mask ? PiiMasker.mask(e.getFailureReason()) : e.getFailureReason(),
                e.getProcessingAttempts(),
                e.getTicketId(),
                e.getDocumentId(),
                e.getReceivedAt(),
                e.getLastAttemptAt(),
                e.getProcessedAt(),
                mask
        );
    }
}
