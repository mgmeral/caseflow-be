package com.caseflow.ai.repository;

import com.caseflow.ai.domain.AiSyncStatus;
import com.caseflow.ai.domain.TicketAiIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketAiIndexRepository extends JpaRepository<TicketAiIndex, Long> {

    Optional<TicketAiIndex> findByTicketId(Long ticketId);

    /**
     * Returns all tickets whose source version exceeds their indexed version — i.e., stale entries.
     * Used by the ingest orchestrator to determine what needs re-indexing.
     */
    @Query("SELECT t FROM TicketAiIndex t WHERE t.sourceVersion > t.indexedVersion")
    List<TicketAiIndex> findStaleEntries();

    /**
     * Returns all entries with the given sync status.
     */
    List<TicketAiIndex> findBySyncStatus(AiSyncStatus status);

    /**
     * Increments the sourceVersion for the given ticket atomically.
     * Returns the number of updated rows (0 if no row exists for the ticket).
     */
    @Modifying
    @Query("""
           UPDATE TicketAiIndex t
           SET t.sourceVersion = t.sourceVersion + 1, t.syncStatus = 'STALE'
           WHERE t.ticketId = :ticketId
           """)
    int incrementSourceVersion(@Param("ticketId") Long ticketId);

    /**
     * Count of entries currently in STALE or FAILED state — used for observability dashboards.
     */
    @Query("SELECT COUNT(t) FROM TicketAiIndex t WHERE t.syncStatus IN ('STALE', 'FAILED')")
    long countStaleOrFailed();
}
