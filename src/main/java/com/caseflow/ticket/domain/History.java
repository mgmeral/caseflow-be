package com.caseflow.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record of a ticket lifecycle event.
 *
 * <p>Events are written once and never mutated. All columns except the optional
 * actor/value fields are non-null after {@code @PrePersist}.
 *
 * <p>New {@code source_type} distinguishes system-generated events (background
 * processes, schedulers) from user-initiated actions so the UI can render the
 * operation log with appropriate attribution.
 */
@Entity
@Table(name = "ticket_history")
public class History {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private Long ticketId;

    /**
     * Stable external UUID of the ticket — denormalised for use in API paths
     * without requiring a join to the tickets table.
     */
    @Column(name = "ticket_public_id", updatable = false)
    private UUID ticketPublicId;

    /**
     * Structured event type (e.g. TICKET_CREATED, STATUS_CHANGED, INBOUND_EMAIL_RECEIVED).
     * Values follow the constants in {@link TicketEventType}.
     */
    @Column(name = "action_type", nullable = false, updatable = false)
    private String actionType;

    /**
     * ID of the authenticated user who triggered the event.
     * Null for system-generated events (source_type = SYSTEM).
     */
    @Column(name = "performed_by", updatable = false)
    private Long performedBy;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private Instant performedAt;

    /**
     * Free-text details — retained for backward compatibility.
     * Prefer {@code summary}, {@code oldValueJson}, {@code newValueJson}, and {@code metadataJson}
     * for new event types.
     */
    @Column(columnDefinition = "TEXT", updatable = false)
    private String details;

    /**
     * SYSTEM — event triggered by a background process (scheduler, routing engine, etc.).
     * USER   — event triggered by an authenticated agent.
     */
    @Column(name = "source_type", nullable = false, updatable = false, length = 50)
    private String sourceType = "USER";

    /** Short human-readable description shown in the UI operation log. */
    @Column(updatable = false, columnDefinition = "TEXT")
    private String summary;

    /** JSON snapshot of the field value before the change (for diff display). */
    @Column(name = "old_value_json", updatable = false, columnDefinition = "TEXT")
    private String oldValueJson;

    /** JSON snapshot of the field value after the change (for diff display). */
    @Column(name = "new_value_json", updatable = false, columnDefinition = "TEXT")
    private String newValueJson;

    /**
     * Event-type-specific metadata as a JSON object.
     * e.g. {"dispatchId": 42, "toAddress": "customer@example.com"} for OUTBOUND_REPLY_QUEUED.
     */
    @Column(name = "metadata_json", updatable = false, columnDefinition = "TEXT")
    private String metadataJson;

    @PrePersist
    private void onCreate() {
        performedAt = Instant.now();
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }

    public UUID getTicketPublicId() { return ticketPublicId; }
    public void setTicketPublicId(UUID ticketPublicId) { this.ticketPublicId = ticketPublicId; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public Long getPerformedBy() { return performedBy; }
    public void setPerformedBy(Long performedBy) { this.performedBy = performedBy; }

    public Instant getPerformedAt() { return performedAt; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getOldValueJson() { return oldValueJson; }
    public void setOldValueJson(String oldValueJson) { this.oldValueJson = oldValueJson; }

    public String getNewValueJson() { return newValueJson; }
    public void setNewValueJson(String newValueJson) { this.newValueJson = newValueJson; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
