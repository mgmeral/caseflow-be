package com.caseflow.email.service;

import com.caseflow.email.domain.EmailIngressEvent;

import java.util.List;

/**
 * Two-stage durable inbound email ingress pipeline.
 *
 * <ul>
 *   <li><b>Stage 1</b> ({@link #receiveEvent}) — store the raw event and return immediately.
 *       Callers (webhook endpoint, IMAP poller) use this to acknowledge receipt quickly.</li>
 *   <li><b>Stage 2</b> ({@link #processEvent}) — full processing: threading, customer-based routing,
 *       ticket create/link, MongoDB document creation.</li>
 * </ul>
 *
 * <p>Routing is customer-based: incoming emails resolve to a Customer via
 * {@code CustomerEmailRoutingRule} entries. Contact records are not used for routing.
 */
public interface EmailIngressService {

    /**
     * Stage 1 — persist an ingress event with status RECEIVED.
     * All email headers and body fields needed for Stage-2 processing are stored here.
     * Safe to call multiple times with the same messageId: subsequent calls are idempotent.
     */
    EmailIngressEvent receiveEvent(IngressEmailData data);

    /**
     * Stage 2 — process a RECEIVED, FAILED, or PROCESSING event through the full pipeline.
     * Updates the event status to PROCESSED, QUARANTINED, or FAILED.
     */
    void processEvent(Long eventId);

    /**
     * Atomically claim a batch of RECEIVED events: transitions each to PROCESSING and
     * returns their IDs. Runs in a short transaction that commits before returning, so
     * the row locks are released and the caller can invoke {@link #processEvent} per ID
     * without any outer transaction — eliminating the PESSIMISTIC_WRITE deadlock.
     */
    List<Long> claimReceivedBatch(int limit);

    /**
     * Atomically claim a batch of FAILED events eligible for retry: transitions each to
     * PROCESSING and returns their IDs. Same transaction isolation rationale as
     * {@link #claimReceivedBatch}.
     */
    List<Long> claimFailedBatch(int maxAttempts, int limit);

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
