package com.caseflow.ai.event.dto;

import java.time.Instant;

/**
 * Base structure for all AI sync events published to Kafka.
 *
 * <p>All fields are designed for idempotency-safe consumption:
 * {@code eventId} is unique per publish, {@code correlationId} traces through the full flow.
 */
public record AiSyncEvent(
        String eventId,
        String correlationId,
        String entityType,
        Long entityId,
        long sourceVersion,
        String operation,
        Instant requestedAt,
        String requestedBy
) {}
