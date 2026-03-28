package com.caseflow.email.api.dto;

import com.caseflow.email.domain.IngressEventStatus;

import java.time.Instant;

public record IngressEventResponse(
        Long id,
        Long mailboxId,
        String messageId,
        String rawFrom,
        String rawSubject,
        Instant receivedAt,
        IngressEventStatus status,
        String failureReason,
        Integer processingAttempts,
        Instant lastAttemptAt,
        Instant processedAt,
        String documentId,
        Long ticketId
) {}
