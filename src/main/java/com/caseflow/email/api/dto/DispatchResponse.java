package com.caseflow.email.api.dto;

import com.caseflow.email.domain.DispatchFailureCategory;
import com.caseflow.email.domain.DispatchStatus;

import java.time.Instant;

public record DispatchResponse(
        Long id,
        Long ticketId,
        Long mailboxId,
        Long sourceIngressEventId,
        Long sentByUserId,
        String messageId,
        String fromAddress,
        String toAddress,
        String resolvedToAddress,
        String subject,
        DispatchStatus status,
        Integer attempts,
        Instant lastAttemptAt,
        Instant sentAt,
        String failureReason,
        DispatchFailureCategory failureCategory,
        Instant scheduledAt,
        Instant createdAt
) {}
