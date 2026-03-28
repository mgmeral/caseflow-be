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
}
