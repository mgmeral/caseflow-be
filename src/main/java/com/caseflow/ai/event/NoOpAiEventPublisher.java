package com.caseflow.ai.event;

import com.caseflow.ai.event.dto.AiSyncEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op AI event publisher — active when {@code caseflow.ai.async.enabled} is {@code false}
 * or not set (default behavior).
 *
 * <p>Logs at WARN level so operators can observe that async sync is skipped.
 * Never throws, never blocks request flow.
 *
 * <p>Sync AI assist endpoints (summary, reply-draft) work normally regardless
 * of which publisher is active.
 */
@Component
@ConditionalOnProperty(name = "caseflow.ai.async.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpAiEventPublisher implements AiEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpAiEventPublisher.class);

    @Override
    public void publishTicketSyncRequested(AiSyncEvent event) {
        log.warn("AI async disabled — ticket-ai-sync-requested NOT published " +
                "[entityId={}, correlationId={}]. Set caseflow.ai.async.enabled=true to enable.",
                event.entityId(), event.correlationId());
    }

    @Override
    public void publishPolicyIngestRequested(AiSyncEvent event) {
        log.warn("AI async disabled — policy-ai-ingest-requested NOT published " +
                "[entityId={}, correlationId={}].",
                event.entityId(), event.correlationId());
    }

    @Override
    public void publishTemplateIngestRequested(AiSyncEvent event) {
        log.warn("AI async disabled — template-ai-ingest-requested NOT published " +
                "[entityId={}, correlationId={}].",
                event.entityId(), event.correlationId());
    }

    @Override
    public boolean isActive() {
        return false;
    }
}
