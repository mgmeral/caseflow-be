package com.caseflow.email.repository;

import com.caseflow.email.domain.EmailMailbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmailMailboxRepository extends JpaRepository<EmailMailbox, Long> {

    Optional<EmailMailbox> findByAddress(String address);

    List<EmailMailbox> findAllByIsActiveTrue();

    List<EmailMailbox> findAllByIsActiveTrueAndPollingEnabledTrue();

    /**
     * Atomically claims a mailbox for polling by this instance if it is not already locked
     * by another instance (or its lease has expired).
     *
     * @return 1 if the claim succeeded, 0 if another instance already holds the lease
     */
    @Modifying
    @Query("""
            UPDATE EmailMailbox m
            SET m.pollLockedBy = :instanceId, m.pollLeasedUntil = :leaseExpiry
            WHERE m.id = :id
              AND m.pollingEnabled = true
              AND m.isActive = true
              AND (m.pollLockedBy IS NULL OR m.pollLeasedUntil < :now)
            """)
    int tryClaimMailbox(@Param("id") Long id,
                        @Param("instanceId") String instanceId,
                        @Param("leaseExpiry") Instant leaseExpiry,
                        @Param("now") Instant now);
}
