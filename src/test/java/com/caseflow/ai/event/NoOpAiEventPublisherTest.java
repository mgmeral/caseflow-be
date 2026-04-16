package com.caseflow.ai.event;

import com.caseflow.ai.event.dto.AiSyncEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests that the no-op publisher:
 * - never throws
 * - reports isActive() = false
 * - handles all event types gracefully
 */
@ExtendWith(MockitoExtension.class)
class NoOpAiEventPublisherTest {

    private final NoOpAiEventPublisher publisher = new NoOpAiEventPublisher();

    @Test
    void isActive_returnsFalse() {
        assertThat(publisher.isActive()).isFalse();
    }

    @Test
    void publishTicketSyncRequested_doesNotThrow() {
        AiSyncEvent event = event("TICKET", 1L);
        assertThatCode(() -> publisher.publishTicketSyncRequested(event))
                .doesNotThrowAnyException();
    }

    @Test
    void publishPolicyIngestRequested_doesNotThrow() {
        AiSyncEvent event = event("POLICY", 10L);
        assertThatCode(() -> publisher.publishPolicyIngestRequested(event))
                .doesNotThrowAnyException();
    }

    @Test
    void publishTemplateIngestRequested_doesNotThrow() {
        AiSyncEvent event = event("TEMPLATE", 20L);
        assertThatCode(() -> publisher.publishTemplateIngestRequested(event))
                .doesNotThrowAnyException();
    }

    @Test
    void publishTicketSyncRequested_doesNotThrow_withNullCorrelationId() {
        AiSyncEvent event = new AiSyncEvent("evt-1", null, "TICKET", 1L, 1L,
                "SYNC", Instant.now(), "system");
        assertThatCode(() -> publisher.publishTicketSyncRequested(event))
                .doesNotThrowAnyException();
    }

    private AiSyncEvent event(String entityType, Long entityId) {
        return new AiSyncEvent("evt-" + entityId, "corr-" + entityId,
                entityType, entityId, 1L, "SYNC", Instant.now(), "system");
    }
}
