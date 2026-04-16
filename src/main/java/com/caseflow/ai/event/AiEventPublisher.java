package com.caseflow.ai.event;

import com.caseflow.ai.event.dto.AiSyncEvent;

/**
 * Abstraction for publishing AI sync events.
 *
 * <p>Two implementations exist:
 * <ul>
 *   <li>{@link KafkaAiEventPublisher} — active when {@code caseflow.ai.async.enabled=true}</li>
 *   <li>{@link NoOpAiEventPublisher} — active by default when async is disabled/unconfigured</li>
 * </ul>
 *
 * <p>The application never fails to start because of this abstraction — if Kafka is not
 * configured, the no-op publisher silently logs and returns without publishing.
 */
public interface AiEventPublisher {

    /**
     * Publishes a ticket AI sync requested event.
     * Implementations must be safe to call even when Kafka is unavailable.
     */
    void publishTicketSyncRequested(AiSyncEvent event);

    /**
     * Publishes a policy ingestion requested event.
     */
    void publishPolicyIngestRequested(AiSyncEvent event);

    /**
     * Publishes a template ingestion requested event.
     */
    void publishTemplateIngestRequested(AiSyncEvent event);

    /**
     * Returns true if this publisher will actually deliver events to an external system.
     */
    boolean isActive();
}
