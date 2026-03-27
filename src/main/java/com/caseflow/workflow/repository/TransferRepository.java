package com.caseflow.workflow.repository;

import com.caseflow.workflow.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    List<Transfer> findByTicketIdOrderByTransferredAtAsc(Long ticketId);
}
