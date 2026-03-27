package com.caseflow.ticket.service;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import com.caseflow.workflow.state.TicketStateMachineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketStateMachineService ticketStateMachineService;
    private final TicketHistoryService ticketHistoryService;

    public TicketService(TicketRepository ticketRepository,
                         TicketStateMachineService ticketStateMachineService,
                         TicketHistoryService ticketHistoryService) {
        this.ticketRepository = ticketRepository;
        this.ticketStateMachineService = ticketStateMachineService;
        this.ticketHistoryService = ticketHistoryService;
    }

    @Transactional
    public Ticket createTicket(String subject, String description, TicketPriority priority,
                               Long customerId, Long createdBy) {
        Ticket ticket = new Ticket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setSubject(subject);
        ticket.setDescription(description);
        ticket.setPriority(priority);
        ticket.setCustomerId(customerId);
        ticket.setStatus(TicketStatus.NEW);
        Ticket saved = ticketRepository.save(ticket);
        ticketHistoryService.recordCreated(saved.getId(), createdBy);
        return saved;
    }

    @Transactional
    public Ticket updateTicket(Long ticketId, String subject, String description, TicketPriority priority) {
        Ticket ticket = findOrThrow(ticketId);
        ticket.setSubject(subject);
        ticket.setDescription(description);
        ticket.setPriority(priority);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket changeStatus(Long ticketId, TicketStatus newStatus, Long performedBy) {
        Ticket ticket = findOrThrow(ticketId);
        TicketStatus previousStatus = ticket.getStatus();
        ticketStateMachineService.validateTransition(previousStatus, newStatus);
        ticket.setStatus(newStatus);
        if (newStatus == TicketStatus.CLOSED) {
            ticket.setClosedAt(Instant.now());
        }
        Ticket saved = ticketRepository.save(ticket);
        ticketHistoryService.recordStatusChanged(ticketId, performedBy,
                previousStatus.name(), newStatus.name());
        return saved;
    }

    @Transactional
    public Ticket closeTicket(Long ticketId, Long performedBy) {
        Ticket ticket = findOrThrow(ticketId);
        ticketStateMachineService.validateTransition(ticket.getStatus(), TicketStatus.CLOSED);
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setClosedAt(Instant.now());
        Ticket saved = ticketRepository.save(ticket);
        ticketHistoryService.recordClosed(ticketId, performedBy);
        return saved;
    }

    @Transactional
    public Ticket reopenTicket(Long ticketId, Long performedBy) {
        Ticket ticket = findOrThrow(ticketId);
        ticketStateMachineService.validateTransition(ticket.getStatus(), TicketStatus.REOPENED);
        ticket.setStatus(TicketStatus.REOPENED);
        Ticket saved = ticketRepository.save(ticket);
        ticketHistoryService.recordReopened(ticketId, performedBy);
        return saved;
    }

    private Ticket findOrThrow(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    private String generateTicketNo() {
        return "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
