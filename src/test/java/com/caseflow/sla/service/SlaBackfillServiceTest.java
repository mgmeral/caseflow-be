package com.caseflow.sla.service;

import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaBackfillServiceTest {

    @Mock
    private SlaService slaService;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private SlaBackfillService backfillService;

    private Ticket ticketA;
    private Ticket ticketB;

    @BeforeEach
    void setUp() {
        ticketA = buildTicket(1L, TicketStatus.NEW, TicketPriority.HIGH);
        ticketB = buildTicket(2L, TicketStatus.IN_PROGRESS, TicketPriority.MEDIUM);
    }

    @Test
    void backfill_stampsEligibleTickets_whenPolicyResolves() {
        Instant firstResponse = Instant.now().plus(60, ChronoUnit.MINUTES);
        Instant resolution    = Instant.now().plus(480, ChronoUnit.MINUTES);

        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(ticketA, ticketB));
        when(slaService.computeDueDates(any(Ticket.class), any(Instant.class)))
                .thenReturn(new Instant[]{firstResponse, resolution});
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        SlaBackfillService.BackfillResult result = backfillService.backfillMissingDueDates();

        assertThat(result.totalEligible()).isEqualTo(2);
        assertThat(result.stamped()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(0);
        assertThat(result.failed()).isEqualTo(0);

        assertThat(ticketA.getResolutionDueAt()).isEqualTo(resolution);
        assertThat(ticketB.getResolutionDueAt()).isEqualTo(resolution);

        verify(ticketRepository, times(2)).save(any(Ticket.class));
    }

    @Test
    void backfill_skipsTickets_whenNoPolicyConfigured() {
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(ticketA));
        when(slaService.computeDueDates(any(Ticket.class), any(Instant.class)))
                .thenReturn(new Instant[]{null, null});

        SlaBackfillService.BackfillResult result = backfillService.backfillMissingDueDates();

        assertThat(result.totalEligible()).isEqualTo(1);
        assertThat(result.stamped()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void backfill_countsFailures_whenComputeThrows() {
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(ticketA));
        when(slaService.computeDueDates(any(Ticket.class), any(Instant.class)))
                .thenThrow(new RuntimeException("DB error"));

        SlaBackfillService.BackfillResult result = backfillService.backfillMissingDueDates();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.stamped()).isEqualTo(0);
    }

    @Test
    void backfill_returnsZeroEligible_whenNoTicketsFound() {
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of());

        SlaBackfillService.BackfillResult result = backfillService.backfillMissingDueDates();

        assertThat(result.totalEligible()).isEqualTo(0);
        assertThat(result.stamped()).isEqualTo(0);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Ticket buildTicket(Long id, TicketStatus status, TicketPriority priority) {
        Ticket t = new Ticket();
        t.setStatus(status);
        t.setPriority(priority);
        try {
            var idField = Ticket.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(t, id);
            var createdAtField = Ticket.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(t, Instant.now().minus(48, ChronoUnit.HOURS));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return t;
    }
}
