package com.caseflow.email.repository;

import com.caseflow.email.domain.DispatchStatus;
import com.caseflow.email.domain.OutboundEmailDispatch;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboundEmailDispatchRepository extends JpaRepository<OutboundEmailDispatch, Long> {

    List<OutboundEmailDispatch> findByTicketId(Long ticketId);

    List<OutboundEmailDispatch> findByStatus(DispatchStatus status);

    /**
     * Fetches up to {@code limit} PENDING dispatches due for sending, with SKIP LOCKED.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT d FROM OutboundEmailDispatch d
            WHERE d.status = 'PENDING'
              AND d.scheduledAt <= CURRENT_TIMESTAMP
            ORDER BY d.scheduledAt ASC
            LIMIT :limit
            """)
    List<OutboundEmailDispatch> findAndLockPending(@Param("limit") int limit);

    /**
     * Fetches up to {@code limit} FAILED dispatches eligible for retry, with SKIP LOCKED.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT d FROM OutboundEmailDispatch d
            WHERE d.status = 'FAILED'
              AND d.attempts < :maxAttempts
            ORDER BY d.lastAttemptAt ASC NULLS FIRST
            LIMIT :limit
            """)
    List<OutboundEmailDispatch> findAndLockFailedForRetry(@Param("maxAttempts") int maxAttempts,
                                                           @Param("limit") int limit);
}
