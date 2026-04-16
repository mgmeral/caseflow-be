package com.caseflow.ai.orchestration;

import com.caseflow.ai.domain.AiSyncStatus;
import com.caseflow.ai.domain.TicketAiIndex;
import com.caseflow.ai.event.AiEventPublisher;
import com.caseflow.ai.event.NoOpAiEventPublisher;
import com.caseflow.ai.event.dto.AiSyncEvent;
import com.caseflow.ai.repository.AiIngestionJobRepository;
import com.caseflow.ai.repository.TicketAiIndexRepository;
import com.caseflow.ai.service.AiSourceVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiIngestOrchestratorTest {

    @Mock private AiEventPublisher eventPublisher;
    @Mock private AiSourceVersionService sourceVersionService;
    @Mock private TicketAiIndexRepository indexRepository;
    @Mock private AiIngestionJobRepository jobRepository;

    @InjectMocks
    private AiIngestOrchestrator orchestrator;

    // ── requestTicketSync ─────────────────────────────────────────────────────

    @Test
    void requestTicketSync_doesNotThrow_whenPublisherActive() {
        when(eventPublisher.isActive()).thenReturn(true);
        when(sourceVersionService.currentSourceVersion(1L)).thenReturn(2L);
        when(indexRepository.findByTicketId(1L)).thenReturn(Optional.empty());
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> orchestrator.requestTicketSync(1L, "user:42"))
                .doesNotThrowAnyException();

        verify(eventPublisher).publishTicketSyncRequested(any(AiSyncEvent.class));
    }

    @Test
    void requestTicketSync_marksJobSkipped_whenPublisherInactive() {
        when(eventPublisher.isActive()).thenReturn(false);
        when(sourceVersionService.currentSourceVersion(1L)).thenReturn(1L);
        when(indexRepository.findByTicketId(1L)).thenReturn(Optional.empty());

        ArgumentCaptor<com.caseflow.ai.domain.AiIngestionJob> captor =
                ArgumentCaptor.forClass(com.caseflow.ai.domain.AiIngestionJob.class);

        orchestrator.requestTicketSync(1L, "user:42");

        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AiSyncStatus.SKIPPED);
        verify(eventPublisher, never()).publishTicketSyncRequested(any());
    }

    @Test
    void requestTicketSync_updatesIndexState_whenPublisherActive() {
        when(eventPublisher.isActive()).thenReturn(true);
        when(sourceVersionService.currentSourceVersion(1L)).thenReturn(3L);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TicketAiIndex index = new TicketAiIndex();
        index.setTicketId(1L);
        when(indexRepository.findByTicketId(1L)).thenReturn(Optional.of(index));

        orchestrator.requestTicketSync(1L, "system");

        verify(indexRepository).save(index);
        assertThat(index.getSyncStatus()).isEqualTo(AiSyncStatus.PENDING);
        assertThat(index.getLastCorrelationId()).isNotNull();
    }

    @Test
    void requestTicketSync_publishesEventWithCorrectEntityType() {
        when(eventPublisher.isActive()).thenReturn(true);
        when(sourceVersionService.currentSourceVersion(5L)).thenReturn(1L);
        when(indexRepository.findByTicketId(5L)).thenReturn(Optional.empty());
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orchestrator.requestTicketSync(5L, "system");

        ArgumentCaptor<AiSyncEvent> captor = ArgumentCaptor.forClass(AiSyncEvent.class);
        verify(eventPublisher).publishTicketSyncRequested(captor.capture());

        AiSyncEvent event = captor.getValue();
        assertThat(event.entityType()).isEqualTo("TICKET");
        assertThat(event.entityId()).isEqualTo(5L);
        assertThat(event.sourceVersion()).isEqualTo(1L);
        assertThat(event.operation()).isEqualTo("SYNC");
    }

    // ── No-op publisher integration ───────────────────────────────────────────

    @Test
    void noOpPublisher_orchestratorDoesNotPublish() {
        NoOpAiEventPublisher noOp = new NoOpAiEventPublisher();
        AiIngestOrchestrator orch = new AiIngestOrchestrator(
                noOp, sourceVersionService, indexRepository, jobRepository);

        when(sourceVersionService.currentSourceVersion(1L)).thenReturn(1L);
        when(indexRepository.findByTicketId(1L)).thenReturn(Optional.empty());

        // Should not throw even though Kafka is not configured
        assertThatCode(() -> orch.requestTicketSync(1L, "system"))
                .doesNotThrowAnyException();
    }
}
