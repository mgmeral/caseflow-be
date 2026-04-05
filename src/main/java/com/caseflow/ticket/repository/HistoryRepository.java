package com.caseflow.ticket.repository;

import com.caseflow.ticket.domain.History;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HistoryRepository extends JpaRepository<History, Long> {

    List<History> findByTicketIdOrderByPerformedAtAsc(Long ticketId);

    List<History> findByTicketPublicIdOrderByPerformedAtAsc(UUID ticketPublicId);
}
