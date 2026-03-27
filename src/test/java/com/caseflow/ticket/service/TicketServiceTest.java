package com.caseflow.ticket.service;

import com.caseflow.common.exception.InvalidTicketStateException;
import com.caseflow.common.exception.TicketNotFoundException;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
