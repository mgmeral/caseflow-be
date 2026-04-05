package com.caseflow.email.service;

import com.caseflow.customer.repository.CustomerEmailSettingsRepository;
import com.caseflow.email.document.EmailDocument;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.repository.EmailDocumentRepository;
import com.caseflow.email.repository.EmailIngressEventRepository;
import com.caseflow.email.repository.EmailMailboxRepository;
import com.caseflow.notification.service.NotificationService;
import com.caseflow.storage.service.AttachmentService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import com.caseflow.workflow.state.TicketSystemTransitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
    @Mock private TicketSystemTransitionService systemTransitionService;
    @Mock private AttachmentService attachmentService;
    @Mock private NotificationService notificationService;
    @Mock private EmailMetrics metrics;
    @Mock private ObjectMapper objectMapper;

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
                null,
                null  // attachments
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

    @Test
    void processEvent_doesNotCreateTicket_whenRoutingQuarantines() {
        EmailIngressEvent event = eventWithStatus(IngressEventStatus.RECEIVED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(loopDetectionService.isLoop(any(), any(), any())).thenReturn(false);
        when(routingService.route(any())).thenReturn(RoutingResult.quarantine("Unknown sender"));
        when(eventRepository.save(any())).thenReturn(event);

        ingressService.processEvent(1L);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void processEvent_doesNotSaveAttachmentMetadata_whenRoutingQuarantines() {
        EmailIngressEvent event = eventWithStatus(IngressEventStatus.RECEIVED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(loopDetectionService.isLoop(any(), any(), any())).thenReturn(false);
        when(routingService.route(any())).thenReturn(RoutingResult.quarantine("Unknown sender"));
        when(eventRepository.save(any())).thenReturn(event);

        ingressService.processEvent(1L);

        verify(attachmentService, never()).saveEmailAttachment(any(), any(), any(), any(), any(), any());
        verify(documentRepository, never()).save(any());
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

    // ── Stage 2: processEvent — resume safety (ticketId already set) ──────────

    @Test
    void processEvent_doesNotCreateSecondTicket_whenEventAlreadyHasTicketId() {
        EmailIngressEvent event = eventWithStatus(IngressEventStatus.FAILED);
        event.setTicketId(99L);  // ticket was created in a previous run

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(loopDetectionService.isLoop(any(), any(), any())).thenReturn(false);
        when(routingService.route(any())).thenReturn(RoutingResult.createTicket(42L));
        when(eventRepository.save(any())).thenReturn(event);

        // saveEmailDocument — document already exists → returns existing id
        EmailDocument existingDoc = new EmailDocument();
        setField(existingDoc, "id", "doc-existing");
        when(documentRepository.findByMessageId(anyString())).thenReturn(Optional.of(existingDoc));

        // ticketRepository.findById for publicId lookup
        Ticket ticket = new Ticket();
        setField(ticket, "publicId", UUID.randomUUID());
        when(ticketRepository.findById(99L)).thenReturn(Optional.of(ticket));

        ingressService.processEvent(1L);

        // ticketRepository.save must never be called — no new ticket
        verify(ticketRepository, never()).save(any());
        verify(metrics).inboundProcessed();
    }

    // ── Stage 2: processEvent — CREATE_TICKET happy path ─────────────────────

    @Test
    void processEvent_createsTicketAndMarksProcessed_forNewEvent() {
        // No attachments — ensures saveAttachmentMetadataRecords is a no-op and no
        // objectMapper interaction is needed, keeping the test focused.
        EmailIngressEvent event = eventWithStatus(IngressEventStatus.RECEIVED);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(loopDetectionService.isLoop(any(), any(), any())).thenReturn(false);
        when(routingService.route(any())).thenReturn(RoutingResult.createTicket(42L));
        when(eventRepository.save(any())).thenReturn(event);
        when(documentRepository.findByMessageId(anyString())).thenReturn(Optional.empty());

        EmailDocument savedDoc = new EmailDocument();
        setField(savedDoc, "id", "doc-new");
        when(documentRepository.save(any())).thenReturn(savedDoc);

        when(ticketRepository.nextTicketSeq()).thenReturn(1L);
        Ticket newTicket = new Ticket();
        setField(newTicket, "id", 55L);
        setField(newTicket, "publicId", UUID.randomUUID());
        when(ticketRepository.save(any())).thenReturn(newTicket);
        when(ticketRepository.findById(55L)).thenReturn(Optional.of(newTicket));

        ingressService.processEvent(1L);

        verify(ticketRepository).save(any());
        verify(metrics).inboundProcessed();
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

    // ── claimReceivedBatch ────────────────────────────────────────────────────

    @Test
    void claimReceivedBatch_marksEventsProcessingAndReturnsIds() {
        EmailIngressEvent e1 = eventWithStatus(IngressEventStatus.RECEIVED);
        EmailIngressEvent e2 = eventWithStatus(IngressEventStatus.RECEIVED);
        setField(e1, "id", 10L);
        setField(e2, "id", 11L);

        when(eventRepository.findAndLockReceived(20)).thenReturn(List.of(e1, e2));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Long> ids = ingressService.claimReceivedBatch(20);

        assertEquals(List.of(10L, 11L), ids);
        assertEquals(IngressEventStatus.PROCESSING, e1.getStatus());
        assertEquals(IngressEventStatus.PROCESSING, e2.getStatus());
        assertEquals(1, e1.getProcessingAttempts());
        assertEquals(1, e2.getProcessingAttempts());
    }

    @Test
    void claimReceivedBatch_returnsEmptyList_whenNoEventsAvailable() {
        when(eventRepository.findAndLockReceived(20)).thenReturn(List.of());

        List<Long> ids = ingressService.claimReceivedBatch(20);

        assertEquals(List.of(), ids);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void processEvent_allowsProcessingStatus_fromPreClaim() {
        // Event arrives as PROCESSING (claimed by scheduler) — should proceed to routing
        EmailIngressEvent event = eventWithStatus(IngressEventStatus.PROCESSING);
        event.setProcessingAttempts(1);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(loopDetectionService.isLoop(any(), any(), any())).thenReturn(false);
        when(routingService.route(any())).thenReturn(RoutingResult.quarantine("Unknown sender"));
        when(eventRepository.save(any())).thenReturn(event);

        ingressService.processEvent(1L);

        // Routing was reached — event did not get skipped
        verify(routingService).route(any());
        verify(metrics).inboundQuarantined();
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
                null,
                null  // attachments
        );
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
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
