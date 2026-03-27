package com.caseflow.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ticket_history")
public class History {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private Long ticketId;

    @Column(name = "action_type", nullable = false, updatable = false)
    private String actionType;

    @Column(name = "performed_by", nullable = false, updatable = false)
    private Long performedBy;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private Instant performedAt;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String details;

    @PrePersist
    private void onCreate() {
        performedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Long getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(Long performedBy) {
        this.performedBy = performedBy;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
