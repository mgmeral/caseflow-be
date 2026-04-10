package com.caseflow.email.api.dto;

import java.time.Instant;

/**
 * Response returned when a ticket reply is successfully enqueued.
 *
 * <p>Provides enough context for the FE to track the dispatch, display the resolved
 * recipient, and correlate back to the originating source event — without an additional
 * round-trip to the dispatch detail endpoint.
 */
public record ReplyEnqueuedResponse(
        Long dispatchId,
        /** Source ingress event this reply threads from; null for proactive sends. */
        Long sourceEventId,
        /** Backend-resolved recipient address (from source event Reply-To/From, or explicit override). */
        String resolvedToAddress,
        /** Sender address — the mailbox address used for SMTP. */
        String fromAddress,
        Long mailboxId,
        String subject,
        Instant acceptedAt
) {}
