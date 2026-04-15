package com.caseflow.sla.repository;

import com.caseflow.sla.domain.SlaEventLog;
import com.caseflow.sla.domain.SlaState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for {@link SlaEventLog} — one row per ticket currently at WARNING or BREACHED.
 */
public interface SlaEventLogRepository extends JpaRepository<SlaEventLog, Long> {

    Optional<SlaEventLog> findByTicketId(Long ticketId);

    /** Deletes the entry when a ticket leaves the at-risk state (terminal or recovered). */
    void deleteByTicketId(Long ticketId);

    /**
     * Returns all log entries currently in the given SLA state.
     * Used by the recovery sweep: find BREACHED/WARNING entries for tickets now terminal.
     */
    List<SlaEventLog> findAllBySlaState(SlaState slaState);

    /** Returns all active (WARNING or BREACHED) log entries regardless of state. */
    List<SlaEventLog> findAllBySlaStateIn(List<SlaState> states);
}
