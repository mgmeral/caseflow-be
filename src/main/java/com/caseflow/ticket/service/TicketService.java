package com.caseflow.ticket.service;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import com.caseflow.workflow.state.TicketStateMachineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

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
        log.info("Creating ticket — priority: {}, customerId: {}, createdBy: {}", priority, customerId, createdBy);
        Ticket ticket = new Ticket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setSubject(subject);
        ticket.setDescription(description);
        ticket.setPriority(priority);
        ticket.setCustomerId(customerId);
        ticket.setStatus(TicketStatus.NEW);
        Ticket saved = ticketRepository.save(ticket);
        ticketHistoryService.recordCreated(saved.getId(), createdBy);
        log.info("Ticket created — ticketId: {}, ticketNo: {}", saved.getId(), saved.getTicketNo());
        return saved;
    }

    @Transactional
    public Ticket updateTicket(Long ticketId, String subject, String description, TicketPriority priority) {
        log.info("Updating ticket {} — priority: {}", ticketId, priority);
        Ticket ticket = findOrThrow(ticketId);
        TicketPriority oldPriority = ticket.getPriority();
        ticket.setSubject(subject);
        ticket.setDescription(description);
        ticket.setPriority(priority);
        Ticket saved = ticketRepository.save(ticket);
        if (priority != null && !priority.equals(oldPriority)) {
            ticketHistoryService.recordPriorityChanged(ticketId, null,
                    oldPriority != null ? oldPriority.name() : "null", priority.name());
        }
        log.info("Ticket {} updated", ticketId);
        return saved;
    }

    @Transactional
    public Ticket changeStatus(Long ticketId, TicketStatus newStatus, Long performedBy) {
        Ticket ticket = findOrThrow(ticketId);
        TicketStatus previousStatus = ticket.getStatus();
        log.info("Changing ticket {} status: {} -> {}, performedBy: {}", ticketId, previousStatus, newStatus, performedBy);
        ticketStateMachineService.validateTransition(previousStatus, newStatus);
        ticket.setStatus(newStatus);
        if (newStatus == TicketStatus.CLOSED) {
            ticket.setClosedAt(Instant.now());
        }
        Ticket saved = ticketRepository.save(ticket);
        ticketHistoryService.recordStatusChanged(ticketId, performedBy,
                previousStatus.name(), newStatus.name());
        log.info("Ticket {} status changed to {}", ticketId, newStatus);
        return saved;
    }

    @Transactional
    public Ticket closeTicket(Long ticketId, Long performedBy) {
        log.info("Closing ticket {} — performedBy: {}", ticketId, performedBy);
        Ticket ticket = findOrThrow(ticketId);
        ticketStateMachineService.validateTransition(ticket.getStatus(), TicketStatus.CLOSED);
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setClosedAt(Instant.now());
        Ticket saved = ticketRepository.save(ticket);
        ticketHistoryService.recordClosed(ticketId, performedBy);
        log.info("Ticket {} closed", ticketId);
        return saved;
    }

    @Transactional
    public Ticket reopenTicket(Long ticketId, Long performedBy) {
        log.info("Reopening ticket {} — performedBy: {}", ticketId, performedBy);
        Ticket ticket = findOrThrow(ticketId);
        ticketStateMachineService.validateTransition(ticket.getStatus(), TicketStatus.REOPENED);
        ticket.setStatus(TicketStatus.REOPENED);
        Ticket saved = ticketRepository.save(ticket);
        ticketHistoryService.recordReopened(ticketId, performedBy);
        log.info("Ticket {} reopened", ticketId);
        return saved;
    }

    private Ticket findOrThrow(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    private String generateTicketNo() {
        return String.format("TKT-%07d", ticketRepository.nextTicketSeq());
    }
}
