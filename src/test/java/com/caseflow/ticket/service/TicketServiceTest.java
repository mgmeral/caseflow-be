package com.caseflow.ticket.service;

import com.caseflow.common.exception.InvalidTicketStateException;
import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.sla.service.SlaService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import com.caseflow.workflow.state.TicketStateMachineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketStateMachineService ticketStateMachineService;

    @Mock
    private TicketHistoryService ticketHistoryService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SlaService slaService;

    @InjectMocks
    private TicketService ticketService;

    private Ticket savedTicket;

    @BeforeEach
    void setUp() {
        savedTicket = new Ticket();
        savedTicket.setTicketNo("TKT-TEST01");
        savedTicket.setSubject("Test subject");
        savedTicket.setStatus(TicketStatus.NEW);
        savedTicket.setPriority(TicketPriority.MEDIUM);
        // Default: no SLA policy → computeDueDates returns nulls.
        // lenient: only createTicket tests call stampSlaDueDates; other tests trigger UnnecessaryStubbing otherwise.
        lenient().when(slaService.computeDueDates(any(Ticket.class), any()))
                .thenReturn(new Instant[]{null, null});
    }

    @Test
    void createTicket_savesTicketAndRecordsHistory() {
        when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);

        Ticket result = ticketService.createTicket(
                "Test subject", "description", TicketPriority.MEDIUM, 1L, 42L
        );

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(TicketStatus.NEW);

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Test subject");
        assertThat(captor.getValue().getPriority()).isEqualTo(TicketPriority.MEDIUM);

        verify(ticketHistoryService).recordCreated(any(), eq(42L));
    }

    @Test
    void createTicket_stampsSlaDueDates_whenPolicyConfigured() {
        Instant now = Instant.now();
        Instant firstResponseDue = now.plus(60, ChronoUnit.MINUTES);
        Instant resolutionDue    = now.plus(480, ChronoUnit.MINUTES);

        when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);
        // Use any() (not any(Instant.class)) because savedTicket.getCreatedAt() is null in this mock context
        when(slaService.computeDueDates(any(Ticket.class), any()))
                .thenReturn(new Instant[]{firstResponseDue, resolutionDue});

        ticketService.createTicket("SLA Test", "desc", TicketPriority.HIGH, 1L, 1L);

        // SLA save: ticketRepository.save called at least twice (initial + SLA stamp)
        verify(ticketRepository, atLeastOnce()).save(any(Ticket.class));
        // Due dates written onto the entity
        assertThat(savedTicket.getFirstResponseDueAt()).isEqualTo(firstResponseDue);
        assertThat(savedTicket.getResolutionDueAt()).isEqualTo(resolutionDue);
    }

    @Test
    void createTicket_doesNotStampSla_whenNoPolicyConfigured() {
        when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);
        when(slaService.computeDueDates(any(Ticket.class), any()))
                .thenReturn(new Instant[]{null, null});

        ticketService.createTicket("No SLA", "desc", TicketPriority.LOW, 1L, 1L);

        // Due dates must remain null — no SLA assigned
        assertThat(savedTicket.getFirstResponseDueAt()).isNull();
        assertThat(savedTicket.getResolutionDueAt()).isNull();
    }

    @Test
    void createTicket_continuesCreation_whenSlaStampThrows() {
        // SLA failure must not block ticket creation (non-critical)
        when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);
        when(slaService.computeDueDates(any(Ticket.class), any()))
                .thenThrow(new RuntimeException("DB unavailable"));

        Ticket result = ticketService.createTicket("Test", "desc", TicketPriority.MEDIUM, 1L, 1L);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(TicketStatus.NEW);
        verify(ticketHistoryService).recordCreated(any(), eq(1L));
    }

    @Test
    void closeTicket_closesAndRecordsHistory() {
        savedTicket.setStatus(TicketStatus.RESOLVED);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(savedTicket));
        when(ticketRepository.save(any())).thenReturn(savedTicket);

        ticketService.closeTicket(1L, 99L);

        assertThat(savedTicket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(savedTicket.getClosedAt()).isNotNull();
        verify(ticketHistoryService).recordClosed(eq(1L), eq(99L));
    }

    @Test
    void closeTicket_throwsInvalidState_whenStateMachineRejects() {
        savedTicket.setStatus(TicketStatus.NEW);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(savedTicket));
        org.mockito.Mockito.doThrow(new InvalidTicketStateException(TicketStatus.NEW, TicketStatus.CLOSED))
                .when(ticketStateMachineService).validateTransition(TicketStatus.NEW, TicketStatus.CLOSED);

        assertThatThrownBy(() -> ticketService.closeTicket(1L, 99L))
                .isInstanceOf(InvalidTicketStateException.class);
    }

    @Test
    void closeTicket_throwsTicketNotFound_whenTicketMissing() {
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.closeTicket(999L, 1L))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void updateTicket_updatesFieldsAndSaves() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(savedTicket));
        when(ticketRepository.save(any())).thenReturn(savedTicket);

        ticketService.updateTicket(1L, "New subject", "New desc", TicketPriority.HIGH);

        assertThat(savedTicket.getSubject()).isEqualTo("New subject");
        assertThat(savedTicket.getPriority()).isEqualTo(TicketPriority.HIGH);
    }

    @Test
    void changeStatus_updatesStatusAndRecordsHistory() {
        savedTicket.setStatus(TicketStatus.NEW);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(savedTicket));
        when(ticketRepository.save(any())).thenReturn(savedTicket);

        ticketService.changeStatus(1L, TicketStatus.TRIAGED, 7L);

        assertThat(savedTicket.getStatus()).isEqualTo(TicketStatus.TRIAGED);
        verify(ticketHistoryService).recordStatusChanged(eq(1L), eq(7L),
                eq("NEW"), eq("TRIAGED"));
    }

    @Test
    void reopenTicket_setsReopenedStatusAndRecordsHistory() {
        savedTicket.setStatus(TicketStatus.CLOSED);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(savedTicket));
        when(ticketRepository.save(any())).thenReturn(savedTicket);

        ticketService.reopenTicket(1L, 5L);

        assertThat(savedTicket.getStatus()).isEqualTo(TicketStatus.REOPENED);
        verify(ticketHistoryService).recordReopened(eq(1L), eq(5L));
    }
}
