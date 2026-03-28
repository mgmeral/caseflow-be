package com.caseflow.email.api.dto;

import com.caseflow.email.domain.DispatchStatus;

import java.time.Instant;

public record DispatchResponse(
        Long id,
        Long ticketId,
        String messageId,
        String fromAddress,
        String toAddress,
        String subject,
        DispatchStatus status,
        Integer attempts,
        Instant lastAttemptAt,
        Instant sentAt,
        String failureReason,
        Instant scheduledAt,
        Instant createdAt
) {}
