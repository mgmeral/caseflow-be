package com.caseflow.email.scheduled.api.dto;

import com.caseflow.email.domain.OutboundEmailDispatch;

import java.time.Instant;

public record ScheduledEmailResponse(
        Long id,
        Long ticketId,
        Long mailboxId,
        String toAddress,
        String subject,
        String status,
        Instant sendNotBefore,
        Instant canceledAt,
        Instant sentAt,
        Instant createdAt
) {
    public static ScheduledEmailResponse from(OutboundEmailDispatch d) {
        return new ScheduledEmailResponse(
                d.getId(),
                d.getTicketId(),
                d.getMailboxId(),
                d.getToAddress(),
                d.getSubject(),
                d.getStatus().name(),
                d.getScheduledAt(),
                d.getCanceledAt(),
                d.getSentAt(),
                d.getCreatedAt()
        );
    }
}
