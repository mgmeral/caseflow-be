package com.caseflow.ai.repository;

import com.caseflow.ai.domain.AiResponseType;
import com.caseflow.ai.domain.TicketAiResponseCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketAiResponseCacheRepository extends JpaRepository<TicketAiResponseCache, Long> {

    /**
     * Finds a cache entry by ticket, source version, and response type.
     * The caller must also check {@link TicketAiResponseCache#isValid()} before using the result.
     */
    Optional<TicketAiResponseCache> findByTicketIdAndSourceVersionAndResponseType(
            Long ticketId, long sourceVersion, AiResponseType responseType);

    /**
     * Returns all non-stale cache entries for the given ticket.
     */
    List<TicketAiResponseCache> findByTicketIdAndIsStaleIsFalse(Long ticketId);

    /**
     * Marks all cache entries for the given ticket as stale.
     * Called when the ticket sourceVersion is incremented.
     */
    @Modifying
    @Query("UPDATE TicketAiResponseCache c SET c.isStale = true WHERE c.ticketId = :ticketId")
    int invalidateByTicketId(@Param("ticketId") Long ticketId);

    /**
     * Deletes expired stale entries to keep the table lean.
     * Safe to call from a scheduled cleanup job.
     */
    @Modifying
    @Query("DELETE FROM TicketAiResponseCache c WHERE c.isStale = true AND c.updatedAt < :before")
    int deleteStaleExpiredBefore(@Param("before") java.time.Instant before);
}
