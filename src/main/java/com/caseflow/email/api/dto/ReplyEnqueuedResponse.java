package com.caseflow.email.api.dto;

import java.time.Instant;

/**
 * Response returned when a ticket reply is successfully enqueued.
 *
 * <p>Provides enough context for the FE to track the dispatch and show
 * the resolved recipient address without an additional round-trip.
 */
public record ReplyEnqueuedResponse(
        Long dispatchId,
        String resolvedToAddress,
        Long mailboxId,
        Instant acceptedAt
) {}
