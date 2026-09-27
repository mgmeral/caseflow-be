package com.caseflow.ai.service;

import com.caseflow.ai.client.AiServiceUnavailableException;
import com.caseflow.ai.client.CaseflowAiClient;
import com.caseflow.ai.client.dto.request.AiTicketIngestRequest;
import com.caseflow.ai.client.dto.response.AiIngestResponse;
import com.caseflow.ai.context.TicketAiContextBuilder;
import com.caseflow.ai.repository.TicketAiIndexRepository;
import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationJobStatus;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.repository.IntegrationJobRepository;
import com.caseflow.integration.service.IntegrationJobExecutionException;
import com.caseflow.integration.service.IntegrationJobService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketEventType;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryRecordedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI similar-case index sync: enqueueing ({@link AiTicketSyncService}), triggering
 * ({@link AiTicketSyncListener}) and executing ({@link AiTicketSyncProcessor}).
 */
class AiTicketSyncTest {

    private static final UUID PUBLIC_ID = UUID.fromString("7d1f7e2a-3b8c-4d5e-9f10-1a2b3c4d5e6f");

    private static Ticket ticket(TicketStatus status) {
        Ticket t = new Ticket();
        try {
            var id = Ticket.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(t, 5L);
            var pid = Ticket.class.getDeclaredField("publicId");
            pid.setAccessible(true);
            pid.set(t, PUBLIC_ID);
        } catch (Exception e) { throw new RuntimeException(e); }
        t.setStatus(status);
        return t;
    }

    @Nested
    class Enqueue {
        private final IntegrationJobService jobService = mock(IntegrationJobService.class);
        private final IntegrationJobRepository jobRepository = mock(IntegrationJobRepository.class);
        private final TicketRepository ticketRepository = mock(TicketRepository.class);
        private final AiAvailabilityService availability = mock(AiAvailabilityService.class);
        private final AiTicketSyncService service =
                new AiTicketSyncService(jobService, jobRepository, ticketRepository, availability);

        @BeforeEach
        void aiOn() {
            when(availability.isAvailable()).thenReturn(true);
        }

        @Test
        void requestSync_enqueuesJobWithUniqueKey() {
            assertThat(service.requestSync(ticket(TicketStatus.RESOLVED), "EVENT")).isTrue();

            verify(jobService).enqueue(eq(IntegrationType.AI_TICKET_SYNC), eq(5L), eq(PUBLIC_ID), isNull(),
                    isNull(), isNull(), org.mockito.ArgumentMatchers.startsWith("ai-ticket-sync:5:"), eq("EVENT"), isNull());
        }

        @Test
        void requestSync_skipsWhenAJobIsAlreadyWaiting() {
            when(jobRepository.existsByTicketIdAndIntegrationTypeAndStatus(
                    5L, IntegrationType.AI_TICKET_SYNC, IntegrationJobStatus.PENDING)).thenReturn(true);

            assertThat(service.requestSync(ticket(TicketStatus.RESOLVED), "EVENT")).isFalse();
            verify(jobService, never()).enqueue(any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void requestSync_skipsWhenAiIsDisabled() {
            when(availability.isAvailable()).thenReturn(false);

            assertThat(service.requestSync(ticket(TicketStatus.RESOLVED), "EVENT")).isFalse();
            verify(jobService, never()).enqueue(any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void reindexAll_enqueuesEveryResolvedAndClosedTicket() {
            Page<Ticket> one = new PageImpl<>(List.of(ticket(TicketStatus.RESOLVED)), PageRequest.of(0, 200), 1);
            when(ticketRepository.findByStatus(eq(TicketStatus.RESOLVED), any())).thenReturn(one);
            when(ticketRepository.findByStatus(eq(TicketStatus.CLOSED), any())).thenReturn(Page.empty());

            assertThat(service.reindexAll()).isEqualTo(1);
            verify(jobService).enqueue(eq(IntegrationType.AI_TICKET_SYNC), eq(5L), any(), any(), any(), any(),
                    anyString(), eq("SYSTEM"), any());
        }
    }

    @Nested
    class Listener {
        private final AiTicketSyncService syncService = mock(AiTicketSyncService.class);
        private final TicketRepository ticketRepository = mock(TicketRepository.class);
        private final AiTicketSyncListener listener = new AiTicketSyncListener(syncService, ticketRepository);

        @Test
        void statusChange_alwaysSyncs_soReopeningRemovesTheTicket() {
            Ticket reopened = ticket(TicketStatus.IN_PROGRESS);
            when(ticketRepository.findById(5L)).thenReturn(Optional.of(reopened));

            listener.onTicketHistoryRecorded(new TicketHistoryRecordedEvent(5L, "REOPENED"));

            verify(syncService).requestSync(reopened, "EVENT");
        }

        @Test
        void contentChange_syncsOnlyWhileResolvedOrClosed() {
            Ticket open = ticket(TicketStatus.IN_PROGRESS);
            Ticket closed = ticket(TicketStatus.CLOSED);
            when(ticketRepository.findById(5L)).thenReturn(Optional.of(open), Optional.of(closed));

            listener.onTicketHistoryRecorded(new TicketHistoryRecordedEvent(5L, TicketEventType.INTERNAL_NOTE_ADDED));
            listener.onTicketHistoryRecorded(new TicketHistoryRecordedEvent(5L, TicketEventType.INTERNAL_NOTE_ADDED));

            verify(syncService, times(1)).requestSync(any(), anyString());
            verify(syncService).requestSync(closed, "EVENT");
        }

        @Test
        void irrelevantAction_isIgnored() {
            listener.onTicketHistoryRecorded(new TicketHistoryRecordedEvent(5L, TicketEventType.JIRA_ISSUE_CREATED));

            verify(ticketRepository, never()).findById(any());
        }
    }

    @Nested
    class Processor {
        private final CaseflowAiClient aiClient = mock(CaseflowAiClient.class);
        private final TicketAiContextBuilder contextBuilder = mock(TicketAiContextBuilder.class);
        private final TicketRepository ticketRepository = mock(TicketRepository.class);
        private final TicketAiIndexRepository indexRepository = mock(TicketAiIndexRepository.class);
        private final IntegrationJobService jobService = mock(IntegrationJobService.class);
        private final AiTicketSyncProcessor processor =
                new AiTicketSyncProcessor(aiClient, contextBuilder, ticketRepository, indexRepository, jobService);

        private IntegrationJob job() {
            IntegrationJob job = new IntegrationJob();
            job.setIntegrationType(IntegrationType.AI_TICKET_SYNC);
            job.setTicketId(5L);
            job.setTicketPublicId(PUBLIC_ID);
            return job;
        }

        @Test
        void resolvedTicket_isIngested() {
            Ticket resolved = ticket(TicketStatus.RESOLVED);
            AiTicketIngestRequest request = new AiTicketIngestRequest(PUBLIC_ID.toString(), null, null, null,
                    "s", "b", null, List.of(), "RESOLVED");
            when(ticketRepository.findById(5L)).thenReturn(Optional.of(resolved));
            when(contextBuilder.buildIngestRequest(resolved)).thenReturn(request);
            when(aiClient.ingestTicket(eq(request), anyString()))
                    .thenReturn(new AiIngestResponse("j", PUBLIC_ID.toString(), 2, "SUCCESS", "ok"));
            when(indexRepository.findByTicketId(5L)).thenReturn(Optional.empty());

            IntegrationJob job = job();
            processor.process(job);

            verify(jobService).markSucceeded(job, "INGESTED:2");
            verify(aiClient, never()).deleteSource(any(), any(), any());
        }

        @Test
        void reopenedTicket_isRemovedFromIndex() {
            when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket(TicketStatus.IN_PROGRESS)));

            IntegrationJob job = job();
            processor.process(job);

            verify(aiClient).deleteSource(eq("TICKET"), eq(PUBLIC_ID.toString()), anyString());
            verify(jobService).markSucceeded(job, "REMOVED");
        }

        @Test
        void deletedTicket_isRemovedByTheJobsPublicId() {
            when(ticketRepository.findById(5L)).thenReturn(Optional.empty());

            processor.process(job());

            verify(aiClient).deleteSource(eq("TICKET"), eq(PUBLIC_ID.toString()), anyString());
        }

        @Test
        void aiUnreachable_isARetryableFailure_butA4xxIsPermanent() {
            when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket(TicketStatus.IN_PROGRESS)));
            org.mockito.Mockito.doThrow(new AiServiceUnavailableException("/x", "down"))
                    .doThrow(new AiServiceUnavailableException("/x", 400, "bad request"))
                    .when(aiClient).deleteSource(any(), any(), any());

            assertThatThrownBy(() -> processor.process(job()))
                    .isInstanceOfSatisfying(IntegrationJobExecutionException.class, e -> assertThat(e.isPermanent()).isFalse());
            assertThatThrownBy(() -> processor.process(job()))
                    .isInstanceOfSatisfying(IntegrationJobExecutionException.class, e -> assertThat(e.isPermanent()).isTrue());
        }

        @Test
        void aiReportsFailedIngest_isARetryableFailure() {
            Ticket resolved = ticket(TicketStatus.CLOSED);
            when(ticketRepository.findById(5L)).thenReturn(Optional.of(resolved));
            when(contextBuilder.buildIngestRequest(resolved)).thenReturn(new AiTicketIngestRequest(
                    PUBLIC_ID.toString(), null, null, null, "s", "b", null, List.of(), "CLOSED"));
            when(aiClient.ingestTicket(any(), anyString()))
                    .thenReturn(new AiIngestResponse("j", PUBLIC_ID.toString(), 0, "FAILED", "qdrant down"));

            assertThatThrownBy(() -> processor.process(job()))
                    .isInstanceOfSatisfying(IntegrationJobExecutionException.class, e -> assertThat(e.isPermanent()).isFalse());
        }
    }
}
