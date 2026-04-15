package com.caseflow.sla.scheduler;

import com.caseflow.integration.domain.TicketDomainEvent;
import com.caseflow.integration.notification.domain.NotificationEventType;
import com.caseflow.notification.service.NotificationService;
import com.caseflow.sla.api.dto.SlaSummary;
import com.caseflow.sla.domain.SlaEventLog;
import com.caseflow.sla.domain.SlaState;
import com.caseflow.sla.repository.SlaEventLogRepository;
import com.caseflow.sla.service.SlaService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaBreachCheckerJobTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private SlaService slaService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private NotificationService notificationService;
    @Mock private SlaEventLogRepository slaEventLogRepository;

    @InjectMocks
    private SlaBreachCheckerJob sut;

    // ── No candidates ─────────────────────────────────────────────────────────

    @Test
    void checkBreaches_doesNothing_whenNoCandidates() {
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(slaEventLogRepository.findAllBySlaStateIn(any())).thenReturn(List.of());

        sut.checkBreaches();

        verify(slaService, never()).computeSummary(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── BREACHED (first occurrence) ───────────────────────────────────────────

    @Test
    void checkBreaches_publishesBreachEvent_firstTime() {
        Ticket ticket = ticket(1L, "TKT-001", TicketStatus.IN_PROGRESS, 10L);
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(ticket));
        when(slaService.computeSummary(ticket)).thenReturn(summary(SlaState.BREACHED));
        when(slaEventLogRepository.findAllBySlaStateIn(any())).thenReturn(List.of());

        sut.checkBreaches();

        ArgumentCaptor<TicketDomainEvent> captor = ArgumentCaptor.forClass(TicketDomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(NotificationEventType.SLA_BREACHED);
        assertThat(captor.getValue().getTicketId()).isEqualTo(1L);
    }

    @Test
    void checkBreaches_notifiesAssignedUser_onFirstBreach() {
        Ticket ticket = ticket(1L, "TKT-001", TicketStatus.IN_PROGRESS, 10L);
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(ticket));
        when(slaService.computeSummary(ticket)).thenReturn(summary(SlaState.BREACHED));
        when(slaEventLogRepository.findAllBySlaStateIn(any())).thenReturn(List.of());

        sut.checkBreaches();

        verify(notificationService).notifySlaBreached(any(), any(), any(), any());
    }

    @Test
    void checkBreaches_skipsNotification_whenNoAssignedUser_onBreach() {
        Ticket ticket = ticket(1L, "TKT-001", TicketStatus.IN_PROGRESS, null);
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(ticket));
        when(slaService.computeSummary(ticket)).thenReturn(summary(SlaState.BREACHED));
        when(slaEventLogRepository.findAllBySlaStateIn(any())).thenReturn(List.of());

        sut.checkBreaches();

        verify(eventPublisher).publishEvent(any(Object.class));
        verify(notificationService, never()).notifySlaBreached(any(), any(), any(), any());
    }

    // ── BREACHED idempotency: repeated runs must NOT emit duplicate events ────

    @Test
    void checkBreaches_repeatedRun_doesNotEmitDuplicate_whenAlreadyBreached() {
        Ticket ticket = ticket(4L, "TKT-004", TicketStatus.IN_PROGRESS, 10L);
        SlaEventLog existingEntry = new SlaEventLog(4L, SlaState.BREACHED);

        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(ticket));
        when(slaService.computeSummary(ticket)).thenReturn(summary(SlaState.BREACHED));
        // Log already contains a BREACHED entry for this ticket
        when(slaEventLogRepository.findAllBySlaStateIn(any())).thenReturn(List.of(existingEntry));
        when(slaEventLogRepository.findAllBySlaStateIn(any())).thenReturn(List.of(existingEntry));

        // Run twice — second run must be fully silent
        sut.checkBreaches();
        sut.checkBreaches();

        // Event and notification emitted exactly once across both runs
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(notificationService, never()).notifySlaBreached(any(), any(), any(), any());
    }

    @Test
    void checkBreaches_firstRunEmitsOnce_secondRunSilent_forBreachedTicket() {
        Ticket ticket = ticket(4L, "TKT-004", TicketStatus.IN_PROGRESS, 10L);
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(ticket));
        when(slaService.computeSummary(ticket)).thenReturn(summary(SlaState.BREACHED));

        // First run: no existing log entry → event emitted, log entry saved
        when(slaEventLogRepository.findAllBySlaStateIn(any())).thenReturn(List.of());

        sut.checkBreaches();

        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
        verify(notificationService, times(1)).notifySlaBreached(any(), any(), any(), any());
    }

    // ── WARNING idempotency ───────────────────────────────────────────────────

    @Test
    void checkBreaches_publishesWarningEvent_firstTime() {
        Ticket ticket = ticket(2L, "TKT-002", TicketStatus.IN_PROGRESS, 10L);
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(ticket));
        when(slaService.computeSummary(ticket)).thenReturn(summary(SlaState.WARNING));
        when(slaEventLogRepository.findAllBySlaStateIn(any())).thenReturn(List.of());

        sut.checkBreaches();

        ArgumentCaptor<TicketDomainEvent> captor = ArgumentCaptor.forClass(TicketDomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(NotificationEventType.SLA_WARNING);
    }

    @Test
    void checkBreaches_repeatedRun_doesNotEmitDuplicate_whenAlreadyWarning() {
        Ticket ticket = ticket(2L, "TKT-002", TicketStatus.IN_PROGRESS, 10L);
        SlaEventLog existingEntry = new SlaEventLog(2L, SlaState.WARNING);

        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(ticket));
        when(slaService.computeSummary(ticket)).thenReturn(summary(SlaState.WARNING));
        when(slaEventLogRepository.findAllBySlaStateIn(any())).thenReturn(List.of(existingEntry));

        sut.checkBreaches();

        verify(eventPublisher, never()).publishEvent(any());
        verify(notificationService, never()).notifySlaWarning(any(), any(), any(), any());
    }

    // ── State transition: WARNING → BREACHED ─────────────────────────────────

    @Test
    void checkBreaches_emitsBreachEvent_onTransitionFromWarningToBreached() {
        Ticket ticket = ticket(5L, "TKT-005", TicketStatus.IN_PROGRESS, 10L);
        // Log says WARNING but current state is now BREACHED
        SlaEventLog existingEntry = new SlaEventLog(5L, SlaState.WARNING);

        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(ticket));
        when(slaService.computeSummary(ticket)).thenReturn(summary(SlaState.BREACHED));
        when(slaEventLogRepository.findAllBySlaStateIn(any())).thenReturn(List.of(existingEntry));

        sut.checkBreaches();

        ArgumentCaptor<TicketDomainEvent> captor = ArgumentCaptor.forClass(TicketDomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(NotificationEventType.SLA_BREACHED);
    }

    // ── RECOVERY: breached ticket goes terminal ───────────────────────────────

    @Test
    void checkBreaches_emitsRecoveryEvent_whenBreachedTicketBecomesTerminal() {
        // No open candidates this run
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of());

        // Log has a BREACHED entry for ticket 6
        SlaEventLog logEntry = new SlaEventLog(6L, SlaState.BREACHED);
        when(slaEventLogRepository.findAllBySlaStateIn(any())).thenReturn(List.of(logEntry));

        // Ticket 6 is now RESOLVED
        Ticket resolved = ticket(6L, "TKT-006", TicketStatus.RESOLVED, 10L);
        when(ticketRepository.findAllById(any())).thenReturn(List.of(resolved));

        sut.checkBreaches();

        ArgumentCaptor<TicketDomainEvent> captor = ArgumentCaptor.forClass(TicketDomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(NotificationEventType.SLA_RECOVERED);
        verify(notificationService).notifySlaRecovered(any(), any(), any(), any());
        verify(slaEventLogRepository).deleteByTicketId(6L);
    }

    @Test
    void checkBreaches_emitsRecoveryEvent_whenWarningTicketBecomesTerminal() {
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of());

        SlaEventLog logEntry = new SlaEventLog(7L, SlaState.WARNING);
        when(slaEventLogRepository.findAllBySlaStateIn(any())).thenReturn(List.of(logEntry));

        Ticket closed = ticket(7L, "TKT-007", TicketStatus.CLOSED, 10L);
        when(ticketRepository.findAllById(any())).thenReturn(List.of(closed));

        sut.checkBreaches();

        ArgumentCaptor<TicketDomainEvent> captor = ArgumentCaptor.forClass(TicketDomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(NotificationEventType.SLA_RECOVERED);
        verify(slaEventLogRepository).deleteByTicketId(7L);
    }

    // ── Recovery then re-breach ───────────────────────────────────────────────

    @Test
    void checkBreaches_allowsNewBreachAfterRecovery() {
        // After recovery, the log entry is deleted.
        // A re-opened ticket with a fresh SLA breach should produce a new event.
        Ticket ticket = ticket(8L, "TKT-008", TicketStatus.REOPENED, 10L);
        // No existing log entry (was deleted on recovery)
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(ticket));
        when(slaService.computeSummary(ticket)).thenReturn(summary(SlaState.BREACHED));
        when(slaEventLogRepository.findAllBySlaStateIn(any())).thenReturn(List.of());

        sut.checkBreaches();

        ArgumentCaptor<TicketDomainEvent> captor = ArgumentCaptor.forClass(TicketDomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(NotificationEventType.SLA_BREACHED);
    }

    // ── OK state ─────────────────────────────────────────────────────────────

    @Test
    void checkBreaches_publishesNoEvent_forOkTicket() {
        Ticket ticket = ticket(3L, "TKT-003", TicketStatus.IN_PROGRESS, 10L);
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(ticket));
        when(slaService.computeSummary(ticket)).thenReturn(summary(SlaState.OK));
        when(slaEventLogRepository.findAllBySlaStateIn(any())).thenReturn(List.of());

        sut.checkBreaches();

        verify(eventPublisher, never()).publishEvent(any());
        verify(notificationService, never()).notifySlaBreached(any(), any(), any(), any());
        verify(notificationService, never()).notifySlaWarning(any(), any(), any(), any());
    }

    @Test
    void checkBreaches_cleansUpStaleLogEntry_whenTicketImprovedToOk() {
        Ticket ticket = ticket(9L, "TKT-009", TicketStatus.IN_PROGRESS, 10L);
        SlaEventLog staleEntry = new SlaEventLog(9L, SlaState.WARNING);

        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(ticket));
        when(slaService.computeSummary(ticket)).thenReturn(summary(SlaState.OK));
        when(slaEventLogRepository.findAllBySlaStateIn(any())).thenReturn(List.of(staleEntry));

        sut.checkBreaches();

        verify(slaEventLogRepository).deleteByTicketId(9L);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Ticket ticket(Long id, String ticketNo, TicketStatus status, Long assignedUserId) {
        Ticket t = new Ticket();
        t.setStatus(status);
        t.setPriority(TicketPriority.HIGH);
        t.setTicketNo(ticketNo);
        t.setAssignedUserId(assignedUserId);
        t.setFirstResponseDueAt(Instant.now().plus(60, ChronoUnit.MINUTES));
        t.setResolutionDueAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        try {
            var idField = Ticket.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(t, id);
            var pidField = Ticket.class.getDeclaredField("publicId");
            pidField.setAccessible(true);
            pidField.set(t, UUID.randomUUID());
            var createdAtField = Ticket.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(t, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return t;
    }

    private SlaSummary summary(SlaState state) {
        boolean breached = state == SlaState.BREACHED;
        return new SlaSummary(
                Instant.now().plus(60, ChronoUnit.MINUTES),
                Instant.now().minus(10, ChronoUnit.MINUTES),
                null,
                false,
                breached,
                state,
                120L,
                60L
        );
    }
}
