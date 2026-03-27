package com.caseflow.ticket.service;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketQueryServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private TicketQueryService ticketQueryService;

    @Test
    void getById_returnsTicket_whenFound() {
        Ticket ticket = makeTicket(1L, "TKT-001");
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        Ticket result = ticketQueryService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTicketNo()).isEqualTo("TKT-001");
    }

    @Test
    void getById_throwsTicketNotFoundException_whenNotFound() {
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketQueryService.getById(99L))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getByTicketNo_returnsTicket_whenFound() {
        Ticket ticket = makeTicket(2L, "TKT-002");
        when(ticketRepository.findByTicketNo("TKT-002")).thenReturn(Optional.of(ticket));

        Ticket result = ticketQueryService.getByTicketNo("TKT-002");
        assertThat(result.getTicketNo()).isEqualTo("TKT-002");
    }

    @Test
    void getByTicketNo_throwsTicketNotFoundException_whenNotFound() {
        when(ticketRepository.findByTicketNo("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketQueryService.getByTicketNo("NOPE"))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessageContaining("NOPE");
    }

    @Test
    void listByStatus_returnsMatchingTickets() {
        Ticket t1 = makeTicket(1L, "TKT-001");
        Ticket t2 = makeTicket(2L, "TKT-002");
        when(ticketRepository.findByStatus(TicketStatus.NEW)).thenReturn(List.of(t1, t2));

        List<Ticket> results = ticketQueryService.listByStatus(TicketStatus.NEW);
        assertThat(results).hasSize(2);
    }

    @Test
    void findAll_returnsAllTickets() {
        when(ticketRepository.findAll()).thenReturn(List.of(makeTicket(1L, "T1"), makeTicket(2L, "T2")));

        List<Ticket> results = ticketQueryService.findAll();
        assertThat(results).hasSize(2);
    }

    private Ticket makeTicket(Long id, String ticketNo) {
        Ticket t = new Ticket();
        t.setTicketNo(ticketNo);
        t.setSubject("Subject " + ticketNo);
        t.setStatus(TicketStatus.NEW);
        t.setPriority(TicketPriority.MEDIUM);
        // Simulate @Id via reflection since no setter
        try {
            var field = Ticket.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(t, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return t;
    }
}
