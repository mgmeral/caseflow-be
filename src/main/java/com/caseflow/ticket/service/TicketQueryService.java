package com.caseflow.ticket.service;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.ticket.repository.TicketSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TicketQueryService {

    private final TicketRepository ticketRepository;

    public TicketQueryService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Transactional(readOnly = true)
    public Ticket getById(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    @Transactional(readOnly = true)
    public Ticket getByTicketNo(String ticketNo) {
        return ticketRepository.findByTicketNo(ticketNo)
                .orElseThrow(() -> new TicketNotFoundException(ticketNo));
    }

    @Transactional(readOnly = true)
    public List<Ticket> listByStatus(TicketStatus status) {
        return ticketRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Ticket> listByAssignedUser(Long userId) {
        return ticketRepository.findByAssignedUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Ticket> listByAssignedGroup(Long groupId) {
        return ticketRepository.findByAssignedGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public List<Ticket> listByCustomer(Long customerId) {
        return ticketRepository.findByCustomerId(customerId);
    }

    @Transactional(readOnly = true)
    public List<Ticket> findAll() {
        return ticketRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Ticket> search(TicketStatus status, TicketPriority priority,
                               Long assignedUserId, Long assignedGroupId, Long customerId,
                               String searchText, Instant from, Instant to, Pageable pageable) {
        Specification<Ticket> spec = Specification.where(TicketSpecification.hasStatus(status))
                .and(TicketSpecification.hasPriority(priority))
                .and(TicketSpecification.hasAssignedUserId(assignedUserId))
                .and(TicketSpecification.hasAssignedGroupId(assignedGroupId))
                .and(TicketSpecification.hasCustomerId(customerId))
                .and(TicketSpecification.subjectOrTicketNoContains(searchText))
                .and(TicketSpecification.createdAfter(from))
                .and(TicketSpecification.createdBefore(to));

        return ticketRepository.findAll(spec, pageable);
    }
}
