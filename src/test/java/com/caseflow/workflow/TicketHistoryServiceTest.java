package com.caseflow.workflow;

import com.caseflow.ticket.domain.History;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketEventType;
import com.caseflow.ticket.repository.HistoryRepository;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketHistoryServiceTest {

    private HistoryRepository historyRepository;
    private TicketRepository ticketRepository;
    private TicketHistoryService service;
    private UUID ticketPublicId;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        historyRepository = mock(HistoryRepository.class);
        ticketRepository  = mock(TicketRepository.class);
        service = new TicketHistoryService(historyRepository, ticketRepository);

        ticketPublicId = UUID.randomUUID();
        ticket = new Ticket();
        // Use reflection to set id (no setter on Ticket)
        try {
            var f = Ticket.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(ticket, 42L);
            var pf = Ticket.class.getDeclaredField("publicId");
            pf.setAccessible(true);
            pf.set(ticket, ticketPublicId);
            var no = Ticket.class.getDeclaredField("ticketNo");
            no.setAccessible(true);
            no.set(ticket, "TKT-0000042");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));
        when(historyRepository.save(any(History.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── TICKET_CREATED ────────────────────────────────────────────────────────

    @Test
    void recordCreated_savesCorrectEventType() {
        History[] saved = captureLastSaved();
        service.recordCreated(42L, 1L);

        assertThat(saved[0].getActionType()).isEqualTo(TicketEventType.TICKET_CREATED);
    }

    @Test
    void recordCreated_setsTicketPublicId() {
        History[] saved = captureLastSaved();
        service.recordCreated(42L, 1L);

        assertThat(saved[0].getTicketPublicId()).isEqualTo(ticketPublicId);
    }

    @Test
    void recordCreated_setsUserSourceType() {
        History[] saved = captureLastSaved();
        service.recordCreated(42L, 99L);

        assertThat(saved[0].getSourceType()).isEqualTo("USER");
    }

    // ── STATUS_CHANGED ────────────────────────────────────────────────────────

    @Test
    void recordStatusChanged_setsOldAndNewValues() {
        History[] saved = captureLastSaved();
        service.recordStatusChanged(42L, 1L, "NEW", "IN_PROGRESS");

        assertThat(saved[0].getActionType()).isEqualTo(TicketEventType.STATUS_CHANGED);
        assertThat(saved[0].getOldValueJson()).isEqualTo("\"NEW\"");
        assertThat(saved[0].getNewValueJson()).isEqualTo("\"IN_PROGRESS\"");
        assertThat(saved[0].getSummary()).contains("NEW").contains("IN_PROGRESS");
    }

    // ── INBOUND_EMAIL_RECEIVED ────────────────────────────────────────────────

    @Test
    void recordInboundEmailReceived_isSystemEvent() {
        History[] saved = captureLastSaved();
        service.recordInboundEmailReceived(42L, ticketPublicId, 7L, "customer@example.com");

        assertThat(saved[0].getActionType()).isEqualTo(TicketEventType.INBOUND_EMAIL_RECEIVED);
        assertThat(saved[0].getSourceType()).isEqualTo("SYSTEM");
        assertThat(saved[0].getPerformedBy()).isNull();
    }

    @Test
    void recordInboundEmailReceived_includesEventIdInMetadata() {
        History[] saved = captureLastSaved();
        service.recordInboundEmailReceived(42L, ticketPublicId, 7L, "customer@example.com");

        assertThat(saved[0].getMetadataJson()).contains("\"ingressEventId\":7");
        assertThat(saved[0].getMetadataJson()).contains("customer@example.com");
    }

    @Test
    void recordInboundEmailReceived_setsTicketPublicId() {
        History[] saved = captureLastSaved();
        service.recordInboundEmailReceived(42L, ticketPublicId, 7L, "from@x.com");

        assertThat(saved[0].getTicketPublicId()).isEqualTo(ticketPublicId);
    }

    // ── OUTBOUND_REPLY_QUEUED ─────────────────────────────────────────────────

    @Test
    void recordOutboundReplyQueued_isUserEvent() {
        History[] saved = captureLastSaved();
        service.recordOutboundReplyQueued(42L, ticketPublicId, 99L, "c@example.com", 5L);

        assertThat(saved[0].getActionType()).isEqualTo(TicketEventType.OUTBOUND_REPLY_QUEUED);
        assertThat(saved[0].getSourceType()).isEqualTo("USER");
        assertThat(saved[0].getPerformedBy()).isEqualTo(5L);
    }

    @Test
    void recordOutboundReplyQueued_includesDispatchIdInMetadata() {
        History[] saved = captureLastSaved();
        service.recordOutboundReplyQueued(42L, ticketPublicId, 99L, "c@example.com", 5L);

        assertThat(saved[0].getMetadataJson()).contains("\"dispatchId\":99");
        assertThat(saved[0].getMetadataJson()).contains("c@example.com");
    }

    // ── OUTBOUND_REPLY_SENT ───────────────────────────────────────────────────

    @Test
    void recordOutboundReplySent_isSystemEvent() {
        History[] saved = captureLastSaved();
        service.recordOutboundReplySent(42L, 55L);

        assertThat(saved[0].getActionType()).isEqualTo(TicketEventType.OUTBOUND_REPLY_SENT);
        assertThat(saved[0].getSourceType()).isEqualTo("SYSTEM");
        assertThat(saved[0].getPerformedBy()).isNull();
    }

    // ── OUTBOUND_REPLY_FAILED ────────────────────────────────────────────────

    @Test
    void recordOutboundReplyFailed_permanent_includesFlagInMetadata() {
        History[] saved = captureLastSaved();
        service.recordOutboundReplyFailed(42L, 55L, "Auth failure", true);

        assertThat(saved[0].getActionType()).isEqualTo(TicketEventType.OUTBOUND_REPLY_FAILED);
        assertThat(saved[0].getMetadataJson()).contains("\"permanent\":true");
    }

    @Test
    void recordOutboundReplyFailed_transient_includesFlagInMetadata() {
        History[] saved = captureLastSaved();
        service.recordOutboundReplyFailed(42L, 55L, "Timeout", false);

        assertThat(saved[0].getMetadataJson()).contains("\"permanent\":false");
    }

    // ── TEMPLATE_USED ─────────────────────────────────────────────────────────

    @Test
    void recordTemplateUsed_includesTemplateCodeInMetadata() {
        History[] saved = captureLastSaved();
        service.recordTemplateUsed(42L, 3L, "CUSTOMER_REPLY", 5L);

        assertThat(saved[0].getActionType()).isEqualTo(TicketEventType.TEMPLATE_USED);
        assertThat(saved[0].getMetadataJson()).contains("\"templateCode\":\"CUSTOMER_REPLY\"");
        assertThat(saved[0].getMetadataJson()).contains("\"templateId\":3");
    }

    // ── ATTACHMENT_ADDED ──────────────────────────────────────────────────────

    @Test
    void recordAttachmentAdded_includesFileNameInSummary() {
        History[] saved = captureLastSaved();
        service.recordAttachmentAdded(42L, 10L, "report.pdf", 5L);

        assertThat(saved[0].getActionType()).isEqualTo(TicketEventType.ATTACHMENT_ADDED);
        assertThat(saved[0].getSummary()).contains("report.pdf");
    }

    // ── getByTicketPublicId ───────────────────────────────────────────────────

    @Test
    void getByTicketPublicId_delegatesToRepository() {
        when(historyRepository.findByTicketPublicIdOrderByPerformedAtAsc(ticketPublicId))
                .thenReturn(List.of());
        List<History> result = service.getByTicketPublicId(ticketPublicId);

        verify(historyRepository).findByTicketPublicIdOrderByPerformedAtAsc(ticketPublicId);
        assertThat(result).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Verify that the saved History has the given actionType. */
    private History historyCaptor(String expectedType) {
        History[] box = new History[1];
        when(historyRepository.save(any(History.class))).thenAnswer(inv -> {
            box[0] = inv.getArgument(0);
            assertThat(box[0].getActionType()).isEqualTo(expectedType);
            return box[0];
        });
        return null; // unused — verify() handles the check
    }

    /** Captures the last History passed to historyRepository.save(). */
    private History[] captureLastSaved() {
        History[] box = new History[1];
        when(historyRepository.save(any(History.class))).thenAnswer(inv -> {
            box[0] = inv.getArgument(0);
            return box[0];
        });
        return box;
    }
}
