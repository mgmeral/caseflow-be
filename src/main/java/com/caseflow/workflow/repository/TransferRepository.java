package com.caseflow.workflow.repository;

import com.caseflow.workflow.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    List<Transfer> findByTicketIdOrderByTransferredAtAsc(Long ticketId);

    /**
     * Counts distinct tickets that have at least one transfer, optionally filtered by customer
     * and creation date window. Uses JPQL with JOIN to the Ticket entity.
     *
     * <p>Note: the {@code :param IS NULL OR col = :param} pattern is safe in JPQL — Hibernate
     * evaluates the null check at the JPQL level before generating SQL.
     */
    @Query("SELECT COUNT(DISTINCT tr.ticketId) FROM Transfer tr " +
           "WHERE EXISTS (SELECT 1 FROM Ticket t WHERE t.id = tr.ticketId " +
           "  AND (:customerId IS NULL OR t.customerId = :customerId) " +
           "  AND (:from IS NULL OR t.createdAt >= :from) " +
           "  AND (:to IS NULL OR t.createdAt <= :to))")
    long countDistinctTransferredTickets(@Param("customerId") Long customerId,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to);
}
