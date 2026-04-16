package com.caseflow.ai.event;

import com.caseflow.ai.event.dto.AiSyncEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka-backed AI event publisher.
 * Only active when {@code caseflow.ai.async.enabled=true}.
 *
 * <p>Uses {@link KafkaTemplate} to publish events asynchronously.
 * Send failures are logged but never propagate to callers — publishing is best-effort.
 * The {@link com.caseflow.ai.domain.AiIngestionJob} row records the intent;
 * operators can re-trigger failed jobs.
 */
@Component
@ConditionalOnProperty(name = "caseflow.ai.async.enabled", havingValue = "true")
public class KafkaAiEventPublisher implements AiEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaAiEventPublisher.class);

    private final KafkaTemplate<String, AiSyncEvent> kafkaTemplate;

    @Value("${caseflow.ai.async.kafka.topic.ticket-sync:ticket-ai-sync-requested}")
    private String ticketSyncTopic;

    @Value("${caseflow.ai.async.kafka.topic.policy-ingest:policy-ai-ingest-requested}")
    private String policyIngestTopic;

    @Value("${caseflow.ai.async.kafka.topic.template-ingest:template-ai-ingest-requested}")
    private String templateIngestTopic;

    public KafkaAiEventPublisher(KafkaTemplate<String, AiSyncEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishTicketSyncRequested(AiSyncEvent event) {
        publish(ticketSyncTopic, event);
    }

    @Override
    public void publishPolicyIngestRequested(AiSyncEvent event) {
        publish(policyIngestTopic, event);
    }

    @Override
    public void publishTemplateIngestRequested(AiSyncEvent event) {
        publish(templateIngestTopic, event);
    }

    @Override
    public boolean isActive() {
        return true;
    }

    private void publish(String topic, AiSyncEvent event) {
        try {
            String key = event.entityType() + ":" + event.entityId();
            kafkaTemplate.send(topic, key, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish AI event to topic={} [entityId={}, correlationId={}]: {}",
                                    topic, event.entityId(), event.correlationId(), ex.getMessage());
                        } else {
                            log.debug("Published AI event to topic={} [entityId={}, correlationId={}, offset={}]",
                                    topic, event.entityId(), event.correlationId(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception ex) {
            // Never let Kafka failures surface to callers
            log.error("Unexpected error publishing AI event to topic={} [entityId={}]: {}",
                    topic, event.entityId(), ex.getMessage());
        }
    }
}
