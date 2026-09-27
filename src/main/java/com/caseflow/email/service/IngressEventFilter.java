package com.caseflow.email.service;

import com.caseflow.email.domain.IngressEventStatus;

import java.time.Instant;
import java.util.List;

/**
 * Optional filters for the ingress event admin list. Every field may be null / empty.
 *
 * @param statuses  any of these statuses; empty = all
 * @param mailboxId filter by mailbox
 * @param messageId exact match on RFC 5322 Message-ID
 * @param ticketId  filter by linked ticket
 * @param q         case-insensitive text match on sender, subject and Message-ID
 *                  (a numeric value also matches the linked ticket id)
 * @param from      received on or after
 * @param to        received on or before
 */
public record IngressEventFilter(
        List<IngressEventStatus> statuses,
        Long mailboxId,
        String messageId,
        Long ticketId,
        String q,
        Instant from,
        Instant to
) {
    public IngressEventFilter {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        q = q == null || q.isBlank() ? null : q.trim();
    }

    public IngressEventFilter withoutStatuses() {
        return new IngressEventFilter(List.of(), mailboxId, messageId, ticketId, q, from, to);
    }
}
