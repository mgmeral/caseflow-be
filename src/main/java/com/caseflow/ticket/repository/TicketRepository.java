package com.caseflow.ticket.repository;

import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    Optional<Ticket> findByTicketNo(String ticketNo);

    Optional<Ticket> findByPublicId(UUID publicId);

    List<Ticket> findByStatus(TicketStatus status);

    List<Ticket> findByAssignedUserId(Long assignedUserId);

    List<Ticket> findByAssignedGroupId(Long assignedGroupId);

    List<Ticket> findByCustomerId(Long customerId);

    boolean existsByTicketNo(String ticketNo);

    @Query(value = "SELECT nextval('ticket_no_seq')", nativeQuery = true)
    Long nextTicketSeq();
}
