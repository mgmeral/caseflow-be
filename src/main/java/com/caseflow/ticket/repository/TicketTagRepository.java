package com.caseflow.ticket.repository;

import com.caseflow.ticket.domain.TicketTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TicketTagRepository extends JpaRepository<TicketTag, TicketTag.TicketTagId> {

    List<TicketTag> findByTicketId(Long ticketId);

    boolean existsByTicketIdAndTagId(Long ticketId, Long tagId);

    void deleteByTicketIdAndTagId(Long ticketId, Long tagId);

    /** Count tickets in a set that have each tag — used for reporting. */
    @Query("""
           SELECT tt.tagId, COUNT(tt.ticketId)
           FROM TicketTag tt
           WHERE tt.ticketId IN :ticketIds
           GROUP BY tt.tagId
           """)
    List<Object[]> countByTagForTickets(@Param("ticketIds") List<Long> ticketIds);

    /**
     * Count tag assignments for a customer's tickets in the given date window.
     * Uses a native SQL join since TicketTag does not have a mapped relationship to Ticket.
     * Null bounds are treated as unbounded.
     */
    @Query(nativeQuery = true, value = """
           SELECT tt.tag_id, COUNT(tt.ticket_id)
           FROM ticket_tags tt
           JOIN tickets t ON t.id = tt.ticket_id
           WHERE t.customer_id = :customerId
           AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR t.created_at >= CAST(:from AS TIMESTAMPTZ))
           AND (CAST(:to   AS TIMESTAMPTZ) IS NULL OR t.created_at <= CAST(:to   AS TIMESTAMPTZ))
           GROUP BY tt.tag_id
           """)
    List<Object[]> countTagsByCustomerAndDateRange(@Param("customerId") Long customerId,
                                                    @Param("from") Instant from,
                                                    @Param("to") Instant to);
}
