package com.caseflow.sla.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persistent record of the last emitted SLA lifecycle state for a ticket.
 *
 * <p>SlaBreachCheckerJob writes one row per at-risk ticket. The row tracks
 * what state was last emitted so repeated scheduler runs skip re-emission
 * for the same state (idempotency guard).
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   (no entry)  →  WARNING  →  BREACHED
 *        ↑                         ↓
 *        └──── row deleted ─────────┘  (on ticket going terminal → RECOVERED emitted)
 *        └──── new entry ───────────   (after re-open, new SLA cycle starts fresh)
 * </pre>
 *
 * <p>The {@code slaState} column stores only active risk states (WARNING, BREACHED).
 * OK / PAUSED / RESOLVED tickets have no entry — their rows are cleaned up.
 */
@Entity
@Table(name = "sla_event_log")
public class SlaEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Internal ticket ID — unique constraint ensures one row per ticket. */
    @Column(name = "ticket_id", nullable = false, unique = true)
    private Long ticketId;

    /** The SLA lifecycle state currently recorded for this ticket. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sla_state", nullable = false, length = 50)
    private SlaState slaState;

    /** When this state was first recorded (or last transitioned). */
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected SlaEventLog() {}

    public SlaEventLog(Long ticketId, SlaState slaState) {
        this.ticketId = ticketId;
        this.slaState = slaState;
        this.recordedAt = Instant.now();
    }

    public Long getId() { return id; }

    public Long getTicketId() { return ticketId; }

    public SlaState getSlaState() { return slaState; }

    public void transition(SlaState newState) {
        this.slaState = newState;
        this.recordedAt = Instant.now();
    }

    public Instant getRecordedAt() { return recordedAt; }
}
