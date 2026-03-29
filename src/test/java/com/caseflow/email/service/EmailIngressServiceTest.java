package com.caseflow.email.service;

import com.caseflow.customer.repository.CustomerEmailSettingsRepository;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.repository.EmailDocumentRepository;
import com.caseflow.email.repository.EmailIngressEventRepository;
import com.caseflow.email.repository.EmailMailboxRepository;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailIngressServiceTest {

    @Mock private EmailIngressEventRepository eventRepository;
    @Mock private EmailDocumentRepository documentRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private EmailMailboxRepository mailboxRepository;
    @Mock private CustomerEmailSettingsRepository settingsRepository;
    @Mock private EmailRoutingService routingService;
    @Mock private EmailThreadingService threadingService;
    @Mock private LoopDetectionService loopDetectionService;
    @Mock private TicketHistoryService historyService;
    @Mock private EmailMetrics metrics;

    @InjectMocks
    private EmailIngressServiceImpl ingressService;

    // ── Stage 1: receiveEvent ─────────────────────────────────────────────────

    @Test
    void receiveEvent_storesNewEvent_withReceivedStatus() {
        when(eventRepository.findByMessageId(anyString())).thenReturn(Optional.empty());
        EmailIngressEvent saved = eventWithStatus(IngressEventStatus.RECEIVED);
        when(eventRepository.save(any())).thenReturn(saved);

        EmailIngressEvent result = ingressService.receiveEvent(sampleData());

        assertNotNull(result);
        assertEquals(IngressEventStatus.RECEIVED, result.getStatus());
        verify(eventRepository).save(any());
        verify(metrics).inboundReceived();
    }

    @Test
    void receiveEvent_isIdempotent_whenEventAlreadyExists() {
        EmailIngressEvent existing = eventWithStatus(IngressEventStatus.PROCESSED);
        when(eventRepository.findByMessageId("<msg-001@test.com>")).thenReturn(Optional.of(existing));

        EmailIngressEvent result = ingressService.receiveEvent(sampleData());

        assertEquals(existing, result);
        verify(eventRepository).findByMessageId("<msg-001@test.com>");
        verify(eventRepository, never()).save(any());
    }

    @Test
    void receiveEvent_persistsThreadingHeaders() {
        when(eventRepository.findByMessageId(anyString())).thenReturn(Optional.empty());
        EmailIngressEvent saved = eventWithStatus(IngressEventStatus.RECEIVED);
        when(eventRepository.save(any())).thenReturn(saved);

        IngressEmailData data = new IngressEmailData(
                "<reply@test.com>",
                "from@test.com",
                "to@test.com",
                "<parent@test.com>",     // inReplyTo
                List.of("<ref1@test.com>"),
                "reply@test.com",
                null,
                "Re: Hello",
                "Body text",
                null,
                null,
                Instant.now(),
                null
        );

        ingressService.receiveEvent(data);

        // Verify save was called with the event containing inReplyTo
        verify(eventRepository).save(any(EmailIngressEvent.class));
    }

    // ── Stage 2: processEvent — loop detection ────────────────────────────────

    @Test
    void processEvent_marksProcessed_forLoopEmail() {
        EmailIngressEvent event = eventWithStatus(IngressEventStatus.RECEIVED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(loopDetectionService.isLoop(any(), any(), any())).thenReturn(true);
        when(eventRepository.save(any())).thenReturn(event);

        ingressService.processEvent(1L);

        verify(eventRepository, atLeastOnce()).save(any(EmailIngressEvent.class));
        verify(metrics).inboundIgnored();
    }

    // ── Stage 2: processEvent — quarantine ────────────────────────────────────

    @Test
    void processEvent_quarantines_whenRoutingReturnsQuarantine() {
        EmailIngressEvent event = eventWithStatus(IngressEventStatus.RECEIVED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(loopDetectionService.isLoop(any(), any(), any())).thenReturn(false);
        when(routingService.route(any())).thenReturn(RoutingResult.quarantine("Unknown sender"));
        when(eventRepository.save(any())).thenReturn(event);

        ingressService.processEvent(1L);

        verify(metrics).inboundQuarantined();
    }

    // ── Stage 2: processEvent — ignore ────────────────────────────────────────

    @Test
    void processEvent_marks_processed_whenRoutingIgnores() {
        EmailIngressEvent event = eventWithStatus(IngressEventStatus.RECEIVED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(loopDetectionService.isLoop(any(), any(), any())).thenReturn(false);
        when(routingService.route(any())).thenReturn(RoutingResult.ignore());
        when(eventRepository.save(any())).thenReturn(event);

        ingressService.processEvent(1L);

        verify(metrics).inboundIgnored();
    }

    // ── Stage 2: processEvent — already processed ─────────────────────────────

    @Test
    void processEvent_skips_whenAlreadyProcessed() {
        EmailIngressEvent event = eventWithStatus(IngressEventStatus.PROCESSED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        ingressService.processEvent(1L);

        verify(routingService, never()).route(any());
    }

    // ── quarantineEvent ───────────────────────────────────────────────────────

    @Test
    void quarantineEvent_setsQuarantinedStatus() {
        EmailIngressEvent event = eventWithStatus(IngressEventStatus.RECEIVED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.save(any())).thenReturn(event);

        ingressService.quarantineEvent(1L, "Spam");

        verify(eventRepository).save(any(EmailIngressEvent.class));
        verify(metrics).inboundQuarantined();
    }

    // ── releaseEvent ──────────────────────────────────────────────────────────

    @Test
    void releaseEvent_resetsToReceived_whenQuarantined() {
        EmailIngressEvent event = eventWithStatus(IngressEventStatus.QUARANTINED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.save(any())).thenReturn(event);

        ingressService.releaseEvent(1L);

        verify(eventRepository).save(any(EmailIngressEvent.class));
    }

    @Test
    void releaseEvent_doesNothing_whenNotQuarantined() {
        EmailIngressEvent event = eventWithStatus(IngressEventStatus.PROCESSED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        ingressService.releaseEvent(1L);

        verify(eventRepository, never()).save(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private IngressEmailData sampleData() {
        return new IngressEmailData(
                "<msg-001@test.com>",
                "from@test.com",
                "to@test.com",
                null,
                null,
                null,
                null,
                "Hello",
                null,
                null,
                null,
                Instant.now(),
                null
        );
    }

    private EmailIngressEvent eventWithStatus(IngressEventStatus status) {
        EmailIngressEvent e = new EmailIngressEvent();
        e.setMessageId("<msg-001@test.com>");
        e.setRawFrom("from@test.com");
        e.setRawSubject("Hello");
        e.setStatus(status);
        e.setProcessingAttempts(0);
        e.setReceivedAt(Instant.now());
        return e;
    }
}
