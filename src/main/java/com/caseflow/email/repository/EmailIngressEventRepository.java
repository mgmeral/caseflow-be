package com.caseflow.email.repository;

import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.IngressEventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;

public interface EmailIngressEventRepository extends JpaRepository<EmailIngressEvent, Long> {

    Optional<EmailIngressEvent> findByMessageId(String messageId);

    List<EmailIngressEvent> findByStatus(IngressEventStatus status);

    /**
     * Fetches up to {@code limit} RECEIVED events, locking them for update with SKIP LOCKED
     * so concurrent workers don't double-process the same event.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT e FROM EmailIngressEvent e
            WHERE e.status = 'RECEIVED'
            ORDER BY e.receivedAt ASC
            LIMIT :limit
            """)
    List<EmailIngressEvent> findAndLockReceived(@Param("limit") int limit);

    /**
     * Fetches up to {@code limit} FAILED events eligible for retry, locking with SKIP LOCKED.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT e FROM EmailIngressEvent e
            WHERE e.status = 'FAILED'
              AND e.processingAttempts < :maxAttempts
            ORDER BY e.lastAttemptAt ASC NULLS FIRST
            LIMIT :limit
            """)
    List<EmailIngressEvent> findAndLockFailedForRetry(@Param("maxAttempts") int maxAttempts,
                                                       @Param("limit") int limit);

    List<EmailIngressEvent> findByTicketId(Long ticketId);

    List<EmailIngressEvent> findByMailboxId(Long mailboxId);
}
