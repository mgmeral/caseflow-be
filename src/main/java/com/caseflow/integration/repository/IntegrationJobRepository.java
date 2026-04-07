package com.caseflow.integration.repository;

import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationJobStatus;
import com.caseflow.integration.domain.IntegrationType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IntegrationJobRepository extends JpaRepository<IntegrationJob, Long> {

    Optional<IntegrationJob> findByIdempotencyKey(String idempotencyKey);

    List<IntegrationJob> findByTicketIdOrderByCreatedAtDesc(Long ticketId);

    List<IntegrationJob> findByTicketIdAndIntegrationTypeOrderByCreatedAtDesc(
            Long ticketId, IntegrationType type);

    /**
     * Counts active (non-terminal) jobs of the given type for a ticket.
     * Used to block duplicate Jira create requests.
     */
    @Query("""
            SELECT COUNT(j) FROM IntegrationJob j
            WHERE j.ticketId = :ticketId
              AND j.integrationType = :type
              AND j.status NOT IN ('SUCCEEDED', 'PERMANENTLY_FAILED', 'CANCELED')
            """)
    long countActiveByTicketAndType(@Param("ticketId") Long ticketId,
                                    @Param("type") IntegrationType type);

    /**
     * Claims up to {@code limit} PENDING jobs due for execution, using SKIP LOCKED.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT j FROM IntegrationJob j
            WHERE j.status = 'PENDING'
              AND j.nextAttemptAt <= :now
            ORDER BY j.nextAttemptAt ASC
            LIMIT :limit
            """)
    List<IntegrationJob> claimPendingBatch(@Param("now") Instant now,
                                           @Param("limit") int limit);

    /**
     * Claims up to {@code limit} FAILED jobs eligible for retry, using SKIP LOCKED.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT j FROM IntegrationJob j
            WHERE j.status = 'FAILED'
              AND j.attemptCount < j.maxAttempts
              AND j.nextAttemptAt <= :now
            ORDER BY j.nextAttemptAt ASC
            LIMIT :limit
            """)
    List<IntegrationJob> claimRetryBatch(@Param("now") Instant now,
                                         @Param("limit") int limit);

    List<IntegrationJob> findByStatus(IntegrationJobStatus status);
}
