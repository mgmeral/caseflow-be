package com.caseflow.ticket.repository;

import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketNo(String ticketNo);

    List<Ticket> findByStatus(TicketStatus status);

    List<Ticket> findByAssignedUserId(Long assignedUserId);

    List<Ticket> findByAssignedGroupId(Long assignedGroupId);

    List<Ticket> findByCustomerId(Long customerId);

    boolean existsByTicketNo(String ticketNo);
}
