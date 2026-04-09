package com.caseflow.email.repository;

import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.IngressEventStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmailIngressEventRepository
        extends JpaRepository<EmailIngressEvent, Long>,
                JpaSpecificationExecutor<EmailIngressEvent> {

    Optional<EmailIngressEvent> findByMessageId(String messageId);

    List<EmailIngressEvent> findByStatus(IngressEventStatus status);

    Page<EmailIngressEvent> findByStatus(IngressEventStatus status, Pageable pageable);

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

    Page<EmailIngressEvent> findByTicketId(Long ticketId, Pageable pageable);

    List<EmailIngressEvent> findByMailboxId(Long mailboxId);

    Page<EmailIngressEvent> findByMailboxId(Long mailboxId, Pageable pageable);

    /**
     * Composite admin filter query.
     * All parameters are optional; null values are not applied as filters.
     */
    @Query("""
            SELECT e FROM EmailIngressEvent e
            WHERE (:status IS NULL OR e.status = :status)
              AND (:mailboxId IS NULL OR e.mailboxId = :mailboxId)
              AND (:messageId IS NULL OR e.messageId = :messageId)
              AND (:ticketId IS NULL OR e.ticketId = :ticketId)
              AND (:from IS NULL OR e.receivedAt >= :from)
              AND (:to IS NULL OR e.receivedAt <= :to)
            """)
    Page<EmailIngressEvent> findFiltered(
            @Param("status") IngressEventStatus status,
            @Param("mailboxId") Long mailboxId,
            @Param("messageId") String messageId,
            @Param("ticketId") Long ticketId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
