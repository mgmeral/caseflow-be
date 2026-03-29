package com.caseflow.email.service;

import com.caseflow.email.domain.EmailIngressEvent;

import java.time.Instant;

/**
 * Two-stage durable inbound email ingress pipeline.
 *
 * <ul>
 *   <li><b>Stage 1</b> ({@link #receiveEvent}) — store the raw event and return immediately.
 *       Callers (webhook endpoint, IMAP poller) use this to acknowledge receipt quickly.</li>
 *   <li><b>Stage 2</b> ({@link #processEvent}) — full processing: threading, routing,
 *       ticket create/link, MongoDB document creation, attachment extraction.</li>
 * </ul>
 */
public interface EmailIngressService {

    /**
     * Stage 1 — persist an ingress event with status RECEIVED.
     * Safe to call multiple times with the same messageId: subsequent calls are idempotent.
     */
    EmailIngressEvent receiveEvent(String messageId, String rawFrom, String rawTo,
                                   String rawSubject, Long mailboxId, Instant receivedAt);

    /**
     * Stage 2 — process a RECEIVED or FAILED event through the full pipeline.
     * Updates the event status to PROCESSED or FAILED/QUARANTINED.
     */
    void processEvent(Long eventId);

    /**
     * Manually quarantine an event with an explicit reason.
     * Useful when an operator identifies an event that should not be auto-processed.
     */
    void quarantineEvent(Long eventId, String reason);

    /**
     * Release a QUARANTINED event back to RECEIVED so the retry scheduler picks it up.
     * Allows operators to re-process events after investigation or rule changes.
     */
    void releaseEvent(Long eventId);
}
